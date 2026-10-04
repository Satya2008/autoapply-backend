package com.naukriradar.core.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.common.exception.PayloadTooLargeException;
import com.naukriradar.core.config.ResumeProperties;
import com.naukriradar.core.dto.response.ResumeResponse;
import com.naukriradar.core.dto.response.ResumeUploadResponse;
import com.naukriradar.core.exception.StorageException;
import com.naukriradar.core.exception.StoredFileNotFoundException;
import com.naukriradar.core.mapper.ResumeMapper;
import com.naukriradar.core.model.DocumentType;
import com.naukriradar.core.model.Resume;
import com.naukriradar.core.model.User;
import com.naukriradar.core.parser.ParsedResume;
import com.naukriradar.core.parser.ResumeParser;
import com.naukriradar.core.repository.ResumeRepository;
import com.naukriradar.core.repository.UserRepository;
import com.naukriradar.core.skill.SkillExtractor;
import com.naukriradar.core.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

/**
 * Upload flow: check size, detect the real type, extract text, store the file, then save
 * the record and skills in one transaction.
 *
 * <p>Parsing and storing happen outside the transaction so a slow PDF never holds a
 * database connection. The file system is not transactional, so we clean up by hand: if
 * the database step fails the new file is deleted, and the old file is deleted only after
 * the new record is committed.
 */
@Service
public class ResumeService {

	private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

	private static final int MAX_FILE_NAME_LENGTH = 200;

	private final UserRepository userRepository;
	private final ResumeRepository resumeRepository;
	private final ProfileService profileService;
	private final ResumeParser parser;
	private final SkillExtractor skillExtractor;
	private final FileStorage storage;
	private final ResumeMapper mapper;
	private final ResumeProperties properties;
	private final ResumeParsingService resumeParsing;
	private final TransactionTemplate transaction;
	private final TransactionTemplate readOnlyTransaction;
	private final Clock clock;

	public ResumeService(UserRepository userRepository, ResumeRepository resumeRepository,
			ProfileService profileService, ResumeParser parser, SkillExtractor skillExtractor,
			FileStorage storage, ResumeMapper mapper, ResumeProperties properties, ResumeParsingService resumeParsing,
			PlatformTransactionManager transactionManager) {
		this.userRepository = userRepository;
		this.resumeRepository = resumeRepository;
		this.profileService = profileService;
		this.parser = parser;
		this.skillExtractor = skillExtractor;
		this.storage = storage;
		this.mapper = mapper;
		this.properties = properties;
		this.resumeParsing = resumeParsing;
		this.transaction = new TransactionTemplate(transactionManager);
		this.readOnlyTransaction = new TransactionTemplate(transactionManager);
		this.readOnlyTransaction.setReadOnly(true);
		this.clock = Clock.systemUTC();
	}

	public ResumeUploadResponse upload(String userId, MultipartFile file) {
		if (!userRepository.existsById(userId)) {
			throw new NotFoundException("No user " + userId + ".");
		}
		checkSize(file);

		ParsedResume parsed = parser.parse(file);
		String sha256 = sha256(file);
		String fileName = safeFileName(file.getOriginalFilename(), parsed.type());

		StoredRef previous = readOnlyTransaction.execute(status -> resumeRepository.findByUserId(userId)
				.map(r -> new StoredRef(r.getStorageKey(), r.getSha256()))
				.orElse(null));

		// Same bytes as the current resume: keep the stored file, just refresh the record.
		boolean sameFile = previous != null && sha256.equals(previous.sha256());
		String key = sameFile ? previous.key() : newKey(userId, parsed.type());
		if (!sameFile) {
			store(key, file);
		}

		Set<String> skillsFound = skillExtractor.extract(parsed.text());
		Saved saved;
		try {
			saved = transaction.execute(status -> {
				User user = userRepository.getReferenceById(userId);
				Resume resume = resumeRepository.findByUserId(userId).orElseGet(() -> new Resume(user));
				String replacedKey = resume.getStorageKey();
				resume.replaceFile(fileName, parsed.type(), file.getSize(), sha256, key, parsed.text(),
						Instant.now(clock));
				resumeRepository.saveAndFlush(resume);
				ProfileService.ResumeSkillsUpdate skills = profileService.applyResumeSkills(userId, skillsFound);
				return new Saved(mapper.toResponse(resume), replacedKey, skills);
			});
		}
		catch (DataIntegrityViolationException ex) {
			// Two first-time uploads raced and the other one won the unique user_id slot.
			cleanUpNewFile(key, sameFile);
			throw new ConflictException("Another upload for this account finished first. Try again.");
		}
		catch (RuntimeException ex) {
			cleanUpNewFile(key, sameFile);
			throw ex;
		}

		if (saved.replacedKey() != null && !saved.replacedKey().equals(key)) {
			deleteQuietly(saved.replacedKey());
		}
		// AI reads it in the background and adds what the dictionary missed
		resumeParsing.parseInBackground(userId);
		return new ResumeUploadResponse(saved.resume(), List.copyOf(skillsFound), saved.skills().added(),
				saved.skills().autoApplyTurnedOff());
	}

	public ResumeResponse get(String userId) {
		return readOnlyTransaction.execute(status -> mapper.toResponse(load(userId)));
	}

	public ResumeFile open(String userId) {
		Resume resume = readOnlyTransaction.execute(status -> load(userId));
		try {
			InputStream content = storage.open(resume.getStorageKey());
			return new ResumeFile(resume.getFileName(), resume.getDocumentType().getContentType(),
					resume.getSizeBytes(), content);
		}
		catch (StoredFileNotFoundException ex) {
			log.warn("Resume {} points at missing file {}", resume.getId(), resume.getStorageKey());
			throw new NotFoundException("The resume file is missing. Please upload it again.");
		}
	}

	/** Where the current resume is stored, for a direct download link. */
	public StoredResume stored(String userId) {
		Resume resume = readOnlyTransaction.execute(status -> load(userId));
		return new StoredResume(resume.getStorageKey(), resume.getFileName(), resume.getDocumentType().getContentType());
	}

	public record StoredResume(String key, String fileName, String contentType) {
	}

	/** Removes the resume and its file. Skills already on the profile stay; the user owns them now. */
	public void delete(String userId) {
		String key = transaction.execute(status -> {
			Resume resume = load(userId);
			resumeRepository.delete(resume);
			resumeRepository.flush();
			return resume.getStorageKey();
		});
		deleteQuietly(key);
	}

	private Resume load(String userId) {
		return resumeRepository.findByUserId(userId)
				.orElseThrow(() -> new NotFoundException("No resume uploaded yet."));
	}

	private void checkSize(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new BadRequestException("The file is empty.");
		}
		long max = properties.maxSize().toBytes();
		if (file.getSize() > max) {
			throw new PayloadTooLargeException("The resume must be at most " + properties.maxSize().toMegabytes() + " MB.");
		}
	}

	private void store(String key, MultipartFile file) {
		try (InputStream in = file.getInputStream()) {
			storage.put(key, in);
		}
		catch (IOException ex) {
			throw new StorageException("Could not read the upload", ex);
		}
	}

	private void cleanUpNewFile(String key, boolean sameFile) {
		if (!sameFile) {
			deleteQuietly(key);
		}
	}

	/** Leftover files cost disk space but break nothing, so a failed delete is only logged. */
	private void deleteQuietly(String key) {
		try {
			storage.delete(key);
		}
		catch (RuntimeException ex) {
			log.warn("Could not delete stored file {}", key, ex);
		}
	}

	private static String newKey(String userId, DocumentType type) {
		return "resumes/" + userId + "/" + UUID.randomUUID() + type.getExtension();
	}

	private static String sha256(MultipartFile file) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 is always available on the JVM", ex);
		}
		try (InputStream in = new DigestInputStream(file.getInputStream(), digest)) {
			in.transferTo(OutputStream.nullOutputStream());
		}
		catch (IOException ex) {
			throw new StorageException("Could not read the upload", ex);
		}
		return HexFormat.of().formatHex(digest.digest());
	}

	/**
	 * Keeps only the last path segment, removes control and quote characters, caps the
	 * length and makes the extension match the real type ("cv" becomes "cv.pdf", and a PDF
	 * named "cv.docx" becomes "cv.pdf").
	 */
	static String safeFileName(String original, DocumentType type) {
		String name = original == null ? "" : original;
		name = name.substring(Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\')) + 1);
		name = name.replaceAll("[\\p{Cntrl}\"<>|*?:]", "").strip();
		name = name.replaceAll("(?i)\\.(pdf|docx|doc)$", "").strip();
		if (name.isEmpty() || name.chars().allMatch(c -> c == '.')) {
			name = "resume";
		}
		int maxBase = MAX_FILE_NAME_LENGTH - type.getExtension().length();
		if (name.length() > maxBase) {
			name = name.substring(0, maxBase).strip();
		}
		return name + type.getExtension().toLowerCase(Locale.ROOT);
	}

	private record StoredRef(String key, String sha256) {
	}

	private record Saved(ResumeResponse resume, String replacedKey, ProfileService.ResumeSkillsUpdate skills) {
	}

}
