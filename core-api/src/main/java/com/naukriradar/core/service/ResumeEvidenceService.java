package com.naukriradar.core.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import com.naukriradar.core.client.MatchingClient;
import com.naukriradar.core.model.Resume;
import com.naukriradar.core.model.ResumeChunk;
import com.naukriradar.core.model.ResumeSection;
import com.naukriradar.core.parser.ResumeChunker;
import com.naukriradar.core.repository.ResumeChunkRepository;
import com.naukriradar.core.repository.ResumeRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The parts of a user's resume that answer a question: chunks the resume (once per file),
 * then asks matching-service's hybrid retrieval which chunks fit. If matching-service can't be
 * reached, falls back to the experience and project chunks in resume order, so callers always
 * get something to work with.
 */
@Service
public class ResumeEvidenceService {

	/** Sections worth showing an AI about a job; header (contact details) and education rarely help. */
	private static final Set<ResumeSection> FALLBACK_SECTIONS = Set.of(ResumeSection.EXPERIENCE, ResumeSection.PROJECTS,
			ResumeSection.SUMMARY);

	private final ResumeRepository resumes;
	private final ResumeChunkRepository chunks;
	private final ResumeChunker chunker;
	private final MatchingClient matching;
	private final TransactionTemplate transaction;
	private final Clock clock = Clock.systemUTC();

	public ResumeEvidenceService(ResumeRepository resumes, ResumeChunkRepository chunks, ResumeChunker chunker,
			MatchingClient matching, PlatformTransactionManager transactionManager) {
		this.resumes = resumes;
		this.chunks = chunks;
		this.chunker = chunker;
		this.matching = matching;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/** Up to {@code top} chunks that best answer the query, best first; empty without a resume. */
	public List<Evidence> evidence(String userId, String query, int top) {
		List<ResumeChunk> all = chunksFor(userId).stream().filter(c -> c.getSection() != ResumeSection.HEADER).toList();
		if (all.isEmpty() || top <= 0) {
			return List.of();
		}
		Map<String, ResumeChunk> byId = new LinkedHashMap<>();
		all.forEach(chunk -> byId.put(chunk.getId(), chunk));
		Map<String, String> passages = new LinkedHashMap<>();
		all.forEach(chunk -> passages.put(chunk.getId(), chunk.getSection().name().toLowerCase(Locale.ROOT) + ": " + chunk.getText()));
		List<String> ranked = matching.retrieve(query, passages, top);
		List<ResumeChunk> picked = new ArrayList<>();
		ranked.stream().map(byId::get).filter(Objects::nonNull).forEach(picked::add);
		if (picked.isEmpty()) {
			all.stream().filter(c -> FALLBACK_SECTIONS.contains(c.getSection()))
					.sorted(Comparator.comparingInt(ResumeChunk::getPosition))
					.limit(top)
					.forEach(picked::add);
		}
		return picked.stream().limit(top).map(c -> new Evidence(c.getSection(), c.getText())).toList();
	}

	/** The user's resume text, for checking claims against; empty without a resume. */
	public Optional<String> resumeText(String userId) {
		return Optional.ofNullable(transaction.execute(status -> resumes.findByUserId(userId)
				.filter(Resume::hasText).map(Resume::getExtractedText).orElse(null)));
	}

	/** The current chunks, cut again if the resume changed since they were made. */
	List<ResumeChunk> chunksFor(String userId) {
		try {
			return rebuildIfStale(userId);
		}
		catch (DataIntegrityViolationException ex) {
			// another request cut the same resume at the same moment; its chunks are just as good
			return chunks.findByUserIdOrderByPositionAsc(userId);
		}
	}

	private List<ResumeChunk> rebuildIfStale(String userId) {
		return transaction.execute(status -> {
			Optional<Resume> resume = resumes.findByUserId(userId).filter(Resume::hasText);
			List<ResumeChunk> existing = chunks.findByUserIdOrderByPositionAsc(userId);
			if (resume.isEmpty()) {
				if (!existing.isEmpty()) {
					chunks.deleteByUserId(userId);
				}
				return List.of();
			}
			String sha = resume.get().getSha256();
			if (!existing.isEmpty() && existing.getFirst().getResumeSha256().equals(sha)) {
				return existing;
			}
			chunks.deleteByUserId(userId);
			chunks.flush();
			Instant now = clock.instant();
			List<ResumeChunker.Chunk> cut = chunker.chunk(resume.get().getExtractedText());
			List<ResumeChunk> fresh = new ArrayList<>(cut.size());
			for (int i = 0; i < cut.size(); i++) {
				fresh.add(new ResumeChunk(userId, sha, cut.get(i).section(), i, cut.get(i).text(), now));
			}
			return chunks.saveAllAndFlush(fresh);
		});
	}

	/** A piece of the resume and the section it is from. */
	public record Evidence(ResumeSection section, String text) {

		/** "[experience] ..." lines, the way prompts get them. */
		public static String format(List<Evidence> evidence) {
			if (evidence.isEmpty()) {
				return "(no resume uploaded)";
			}
			StringBuilder text = new StringBuilder();
			for (Evidence e : evidence) {
				text.append('[').append(e.section().name().toLowerCase(Locale.ROOT)).append("] ").append(e.text().strip()).append("\n\n");
			}
			return text.toString().strip();
		}

	}

}
