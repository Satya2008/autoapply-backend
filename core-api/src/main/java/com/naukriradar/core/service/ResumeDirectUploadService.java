package com.naukriradar.core.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.common.exception.PayloadTooLargeException;
import com.naukriradar.core.config.ResumeProperties;
import com.naukriradar.core.config.StorageProperties;
import com.naukriradar.core.dto.request.ConfirmUploadRequest;
import com.naukriradar.core.dto.request.DirectUploadRequest;
import com.naukriradar.core.dto.response.DirectUploadResponse;
import com.naukriradar.core.dto.response.DownloadUrlResponse;
import com.naukriradar.core.dto.response.ResumeUploadResponse;
import com.naukriradar.core.exception.StorageException;
import com.naukriradar.core.repository.UserRepository;
import com.naukriradar.core.storage.FileStorage;
import com.naukriradar.core.storage.PresignedUrls;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Resume upload and download straight between the browser and object storage, through
 * presigned links: the file never passes through our servers on the way in or out.
 *
 * <ol>
 * <li>{@link #uploadUrl}: a PUT link for a new key under {@code uploads/<user>/}.</li>
 * <li>The browser PUTs the file there.</li>
 * <li>{@link #confirm}: we read it back, run the usual checks and parsing, store it as the
 * resume and remove the upload.</li>
 * </ol>
 *
 * Only works with S3 storage; with local disk, the multipart upload is the way.
 */
@Service
public class ResumeDirectUploadService {

	private static final Logger log = LoggerFactory.getLogger(ResumeDirectUploadService.class);

	static final Set<String> ACCEPTED_TYPES = Set.of("application/pdf",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document");

	private final FileStorage storage;
	private final ResumeService resumes;
	private final UserRepository users;
	private final ResumeProperties resumeProperties;
	private final Duration validFor;
	private final Clock clock = Clock.systemUTC();

	public ResumeDirectUploadService(FileStorage storage, ResumeService resumes, UserRepository users,
			ResumeProperties resumeProperties, StorageProperties storageProperties) {
		this.storage = storage;
		this.resumes = resumes;
		this.users = users;
		this.resumeProperties = resumeProperties;
		this.validFor = storageProperties.s3().presignFor();
	}

	public DirectUploadResponse uploadUrl(String userId, DirectUploadRequest request) {
		PresignedUrls presigned = presigned();
		if (!users.existsById(userId)) {
			throw new NotFoundException("No user " + userId + ".");
		}
		String contentType = request.contentType().strip().toLowerCase();
		if (!ACCEPTED_TYPES.contains(contentType)) {
			throw new BadRequestException("Upload a PDF or a Word (.docx) file.");
		}
		checkSize(request.sizeBytes());
		String key = pendingPrefix(userId) + UUID.randomUUID();
		return new DirectUploadResponse(presigned.uploadUrl(key, contentType, validFor), "PUT",
				Map.of("Content-Type", contentType), key, clock.instant().plus(validFor));
	}

	public ResumeUploadResponse confirm(String userId, ConfirmUploadRequest request) {
		PresignedUrls presigned = presigned();
		String key = request.key().strip();
		// a key from someone else's link, or made up, is simply not found
		if (!key.startsWith(pendingPrefix(userId)) || key.contains("..")) {
			throw new NotFoundException("No upload " + key + ".");
		}
		OptionalLong size = presigned.size(key);
		if (size.isEmpty()) {
			throw new NotFoundException("Nothing was uploaded to " + key + " (or the link expired).");
		}
		try {
			checkSize(size.getAsLong());
			byte[] bytes;
			try (InputStream in = storage.open(key)) {
				bytes = in.readAllBytes();
			}
			catch (IOException ex) {
				throw new StorageException("Could not read the upload", ex);
			}
			String fileName = request.fileName() == null || request.fileName().isBlank() ? "resume" : request.fileName();
			return resumes.upload(userId, new StoredUpload(fileName, null, bytes));
		}
		finally {
			try {
				storage.delete(key);
			}
			catch (RuntimeException ex) {
				log.warn("Could not remove the pending upload {}", key, ex);
			}
		}
	}

	public DownloadUrlResponse downloadUrl(String userId) {
		PresignedUrls presigned = presigned();
		ResumeService.StoredResume stored = resumes.stored(userId);
		return new DownloadUrlResponse(presigned.downloadUrl(stored.key(), stored.fileName(), stored.contentType(), validFor),
				clock.instant().plus(validFor));
	}

	/** Whether links can be made here at all. */
	public boolean available() {
		return storage instanceof PresignedUrls;
	}

	private PresignedUrls presigned() {
		if (storage instanceof PresignedUrls presigned) {
			return presigned;
		}
		throw new BadRequestException("Direct upload needs S3 storage. Use POST /api/v1/me/resume instead.");
	}

	private void checkSize(long size) {
		if (size > resumeProperties.maxSize().toBytes()) {
			throw new PayloadTooLargeException("The resume must be at most " + resumeProperties.maxSize().toMegabytes() + " MB.");
		}
	}

	private static String pendingPrefix(String userId) {
		return "uploads/" + userId + "/";
	}

}
