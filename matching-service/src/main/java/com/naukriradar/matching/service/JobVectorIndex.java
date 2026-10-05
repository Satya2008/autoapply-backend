package com.naukriradar.matching.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;

import com.naukriradar.matching.config.MatchingProperties;
import com.naukriradar.matching.config.SemanticProperties;
import com.naukriradar.matching.embedding.Vectors;
import com.naukriradar.matching.repository.EmbeddingStore;
import org.springframework.stereotype.Component;

/**
 * The job vectors of one model, in memory, searched by brute force. A few thousand recent jobs
 * of a few hundred dimensions is a few MB and a few milliseconds per search: no vector
 * database needed at this size (docs/adr/0001-vector-store.md says when that changes).
 *
 * <p>Each instance keeps its own copy and catches up from the database incrementally, so
 * vectors written by another instance show up here too.
 */
@Component
public class JobVectorIndex {

	/** How often a search first catches up with the database. */
	static final Duration REFRESH_EVERY = Duration.ofSeconds(30);

	/**
	 * Re-reads this much before the last change seen: a row whose transaction committed late
	 * can carry an older updated_at than one already read.
	 */
	static final Duration OVERLAP = Duration.ofMinutes(2);

	private static final int PAGE = 2000;

	private final EmbeddingStore store;
	private final SemanticProperties semantic;
	private final MatchingProperties matching;
	private final Clock clock = Clock.systemUTC();
	private final Object refreshLock = new Object();

	private volatile Snapshot snapshot = new Snapshot("");

	public JobVectorIndex(EmbeddingStore store, SemanticProperties semantic, MatchingProperties matching) {
		this.store = store;
		this.semantic = semantic;
		this.matching = matching;
	}

	/**
	 * The {@code k} jobs most similar to the vector, posted within the matching window.
	 * Vectors of another model are never compared: asking for a new model reloads the index.
	 */
	public List<Hit> search(float[] query, String modelKey, int k) {
		if (k <= 0 || query == null || query.length == 0) {
			return List.of();
		}
		Snapshot current = snapshot;
		if (!current.modelKey.equals(modelKey) || current.refreshedAt.isBefore(clock.instant().minus(REFRESH_EVERY))) {
			refresh(modelKey);
			current = snapshot;
		}
		Instant since = since();
		PriorityQueue<Hit> best = new PriorityQueue<>(Comparator.comparingDouble(Hit::similarity));
		for (Map.Entry<String, Entry> entry : current.entries.entrySet()) {
			Entry job = entry.getValue();
			if (job.postedAt() != null && job.postedAt().isBefore(since)) {
				continue;
			}
			double similarity = Vectors.cosine(query, job.vector());
			if (best.size() < k) {
				best.add(new Hit(entry.getKey(), similarity));
			}
			else if (similarity > best.peek().similarity()) {
				best.poll();
				best.add(new Hit(entry.getKey(), similarity));
			}
		}
		List<Hit> hits = new ArrayList<>(best);
		hits.sort(Comparator.comparingDouble(Hit::similarity).reversed());
		return hits;
	}

	/** Catches up with the database; a different model than held means a full reload. */
	public void refresh(String modelKey) {
		synchronized (refreshLock) {
			Snapshot current = snapshot.modelKey.equals(modelKey) ? snapshot : new Snapshot(modelKey);
			Instant since = since();
			Instant afterAt = current.seenUpTo.minus(OVERLAP);
			String[] afterId = { "" };
			Instant[] cursor = { afterAt };
			Snapshot target = current;
			int read;
			do {
				read = store.forEachJobVector(modelKey, cursor[0], afterId[0], since, PAGE, (jobId, vector, postedAt, updatedAt) -> {
					target.entries.put(jobId, new Entry(vector, postedAt));
					cursor[0] = updatedAt;
					afterId[0] = jobId;
					if (updatedAt.isAfter(target.seenUpTo)) {
						target.seenUpTo = updatedAt;
					}
				});
			}
			while (read == PAGE);
			evict(target, since);
			target.refreshedAt = clock.instant();
			snapshot = target;
		}
	}

	/** Vectors just written by this instance: searchable at once, without waiting for a refresh. */
	public void put(String modelKey, Map<String, Entry> vectors) {
		Snapshot current = snapshot;
		if (current.modelKey.equals(modelKey)) {
			current.entries.putAll(vectors);
		}
	}

	public int size() {
		return snapshot.entries.size();
	}

	public String modelKey() {
		return snapshot.modelKey;
	}

	private Instant since() {
		return clock.instant().minus(Duration.ofDays(matching.candidateDays()));
	}

	/** Drops jobs too old to be matched, then the oldest beyond the cap. */
	private void evict(Snapshot target, Instant since) {
		target.entries.values().removeIf(e -> e.postedAt() != null && e.postedAt().isBefore(since));
		int excess = target.entries.size() - semantic.maxIndexedJobs();
		if (excess > 0) {
			target.entries.entrySet().stream()
					.sorted(Comparator.comparing(e -> e.getValue().postedAt() == null ? Instant.EPOCH : e.getValue().postedAt()))
					.limit(excess)
					.map(Map.Entry::getKey)
					.toList()
					.forEach(target.entries::remove);
		}
	}

	public record Entry(float[] vector, Instant postedAt) {
	}

	/** @param similarity cosine, -1 to 1 */
	public record Hit(String jobId, double similarity) {
	}

	private static final class Snapshot {

		final String modelKey;

		final Map<String, Entry> entries = new ConcurrentHashMap<>();

		volatile Instant seenUpTo = Instant.EPOCH.plus(OVERLAP);

		volatile Instant refreshedAt = Instant.EPOCH;

		Snapshot(String modelKey) {
			this.modelKey = modelKey;
		}

	}

}
