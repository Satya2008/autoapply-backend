package com.naukriradar.matching.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.naukriradar.common.redis.lock.DistributedLock;
import com.naukriradar.common.redis.lock.LockHandle;
import com.naukriradar.common.redis.lock.LockUnavailableException;
import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.client.JobServiceClient;
import com.naukriradar.matching.client.UpstreamException;
import com.naukriradar.matching.config.MatchingProperties;
import com.naukriradar.matching.config.SemanticProperties;
import com.naukriradar.matching.embedding.EmbeddingService;
import com.naukriradar.matching.embedding.EmbeddingTexts;
import com.naukriradar.matching.embedding.EmbeddingUnavailableException;
import com.naukriradar.matching.embedding.Vectors;
import com.naukriradar.matching.repository.EmbeddingStore;
import com.naukriradar.matching.repository.EmbeddingStore.NewJobVector;
import com.naukriradar.matching.repository.EmbeddingStore.StoredVector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Keeps job vectors up to date. A job is embedded once per model and again only when its text
 * changes (the text hash tells). Two ways in: {@link #ensure} during a match run, for the
 * shortlisted jobs; {@link #catchUp} for every recent job, after new jobs arrive and nightly,
 * so the vector search can find jobs the keyword search missed.
 */
@Service
public class JobEmbeddingIndexer {

	private static final Logger log = LoggerFactory.getLogger(JobEmbeddingIndexer.class);

	static final String LOCK = "embedding-index";

	private final EmbeddingService embeddings;
	private final EmbeddingStore store;
	private final JobVectorIndex index;
	private final JobServiceClient jobService;
	private final DistributedLock locks;
	private final SemanticProperties semantic;
	private final MatchingProperties matching;
	private final Clock clock = Clock.systemUTC();

	public JobEmbeddingIndexer(EmbeddingService embeddings, EmbeddingStore store, JobVectorIndex index,
			JobServiceClient jobService, DistributedLock locks, SemanticProperties semantic, MatchingProperties matching) {
		this.embeddings = embeddings;
		this.store = store;
		this.index = index;
		this.jobService = jobService;
		this.locks = locks;
		this.semantic = semantic;
		this.matching = matching;
	}

	/**
	 * Vectors of these jobs under this model, embedding the missing and changed ones. Best
	 * effort: if embedding fails, a changed job keeps its old vector and a new one has none
	 * (the semantic factor then says "can't tell" for it).
	 */
	public Ensured ensure(List<CandidateJob> jobs, String modelKey) {
		Map<String, CandidateJob> byId = new LinkedHashMap<>();
		jobs.stream().filter(job -> job.id() != null).forEach(job -> byId.putIfAbsent(job.id(), job));
		if (byId.isEmpty()) {
			return new Ensured(Map.of(), 0, null);
		}
		Map<String, StoredVector> stored = store.jobVectors(modelKey, byId.keySet());
		Map<String, float[]> vectors = new HashMap<>();
		List<CandidateJob> toEmbed = new ArrayList<>();
		List<String> texts = new ArrayList<>();
		List<String> hashes = new ArrayList<>();
		for (CandidateJob job : byId.values()) {
			String text = EmbeddingTexts.job(job);
			String hash = Vectors.hash(text);
			StoredVector existing = stored.get(job.id());
			if (existing != null) {
				vectors.put(job.id(), existing.vector());
			}
			if (existing == null || !existing.textHash().equals(hash)) {
				toEmbed.add(job);
				texts.add(text);
				hashes.add(hash);
			}
		}
		if (toEmbed.isEmpty()) {
			return new Ensured(vectors, 0, null);
		}
		List<float[]> made;
		try {
			made = embeddings.embed(texts, modelKey);
		}
		catch (EmbeddingUnavailableException ex) {
			log.warn("Could not embed {} job(s) with {}: {}", toEmbed.size(), modelKey, ex.getMessage());
			return new Ensured(vectors, 0, ex.getMessage());
		}
		Instant now = clock.instant();
		List<NewJobVector> rows = new ArrayList<>(toEmbed.size());
		Map<String, JobVectorIndex.Entry> fresh = new HashMap<>();
		for (int i = 0; i < toEmbed.size(); i++) {
			CandidateJob job = toEmbed.get(i);
			float[] vector = made.get(i);
			if (vector.length == 0) {
				continue;
			}
			rows.add(new NewJobVector(job.id(), hashes.get(i), vector, job.postedAt()));
			fresh.put(job.id(), new JobVectorIndex.Entry(vector, job.postedAt()));
			vectors.put(job.id(), vector);
		}
		store.saveJobVectors(modelKey, rows, now);
		index.put(modelKey, fresh);
		return new Ensured(vectors, rows.size(), null);
	}

	/**
	 * Embeds every recent active job that has no current vector yet, page by page. One instance
	 * at a time; the others skip.
	 */
	public CatchUp catchUp() {
		Optional<LockHandle> lock;
		try {
			lock = locks.tryAcquire(LOCK, Duration.ofMinutes(5));
		}
		catch (LockUnavailableException ex) {
			return CatchUp.skipped("Redis is unavailable");
		}
		if (lock.isEmpty()) {
			return CatchUp.skipped("another instance is indexing");
		}
		try (LockHandle handle = lock.get()) {
			String modelKey = embeddings.currentModelKey();
			int seen = 0;
			int embedded = 0;
			String after = null;
			String problem = null;
			while (handle.isValid()) {
				List<CandidateJob> page = jobService.recent(matching.candidateDays(), after, semantic.indexPageSize());
				if (page.isEmpty()) {
					break;
				}
				Ensured ensured = ensure(page, modelKey);
				seen += page.size();
				embedded += ensured.embedded();
				if (ensured.problem() != null) {
					// the provider is failing: stop instead of hammering it page after page
					problem = ensured.problem();
					break;
				}
				after = page.getLast().id();
				if (page.size() < semantic.indexPageSize()) {
					break;
				}
			}
			index.refresh(modelKey);
			log.info("Vector index caught up for {}: {} job(s) checked, {} embedded{}", modelKey, seen, embedded,
					problem == null ? "" : "; stopped: " + problem);
			return new CatchUp(modelKey, seen, embedded, problem);
		}
		catch (UpstreamException ex) {
			return CatchUp.skipped(ex.getMessage());
		}
	}

	/** Right after start, in the background: the service is usable while it runs. */
	@EventListener(ApplicationReadyEvent.class)
	public void catchUpOnStart() {
		if (!semantic.enabled() || !semantic.indexOnStartup()) {
			return;
		}
		Thread.ofVirtual().name("vector-index-startup").start(() -> {
			try {
				catchUp();
			}
			catch (RuntimeException ex) {
				log.warn("Vector index catch-up at startup failed; it is retried after new jobs arrive and nightly", ex);
			}
		});
	}

	/**
	 * @param vectors by job id
	 * @param problem why some jobs couldn't be embedded, or null
	 */
	public record Ensured(Map<String, float[]> vectors, int embedded, String problem) {
	}

	/**
	 * @param modelKey null when skipped
	 * @param problem why it stopped early or was skipped, or null
	 */
	public record CatchUp(String modelKey, int jobsChecked, int jobsEmbedded, String problem) {

		static CatchUp skipped(String reason) {
			return new CatchUp(null, 0, 0, reason);
		}

	}

}
