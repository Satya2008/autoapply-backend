package com.naukriradar.matching.repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.naukriradar.matching.embedding.Vectors;
import com.naukriradar.matching.util.UuidV7;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Vectors in MySQL as float32 blobs: job vectors per model, and the cache of paid
 * embeddings. Searching happens in memory ({@code JobVectorIndex}); see
 * docs/adr/0001-vector-store.md for why there is no vector database.
 */
@Repository
public class EmbeddingStore {

	private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

	/** IN lists are cut into chunks of this size. */
	private static final int CHUNK = 500;

	private static final String UPSERT_JOB = """
			INSERT INTO job_embeddings (id, job_id, model_key, text_hash, dimensions, vector, posted_at, updated_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?) AS new
			ON DUPLICATE KEY UPDATE text_hash = new.text_hash, dimensions = new.dimensions, vector = new.vector,
			  posted_at = new.posted_at, updated_at = new.updated_at
			""";

	private final JdbcTemplate jdbc;
	private final NamedParameterJdbcTemplate namedJdbc;

	public EmbeddingStore(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
	}

	/** Cached vectors by text hash; hashes not cached are simply missing from the map. */
	public Map<String, float[]> cached(String modelKey, Collection<String> textHashes) {
		Map<String, float[]> found = new HashMap<>();
		for (List<String> chunk : chunks(textHashes)) {
			namedJdbc.query("SELECT text_hash, vector FROM embedding_cache WHERE model_key = :model AND text_hash IN (:hashes)",
					new MapSqlParameterSource("model", modelKey).addValue("hashes", chunk),
					rs -> {
						found.put(rs.getString("text_hash"), Vectors.fromBytes(rs.getBytes("vector")));
					});
		}
		return found;
	}

	/** Two instances caching the same text at once is fine: the second insert is ignored. */
	public void cache(String modelKey, String textHash, float[] vector, Instant now) {
		jdbc.update(con -> {
			var ps = con.prepareStatement("INSERT IGNORE INTO embedding_cache (id, model_key, text_hash, dimensions, vector,"
					+ " created_at) VALUES (?, ?, ?, ?, ?, ?)");
			ps.setString(1, UuidV7.at(now));
			ps.setString(2, modelKey);
			ps.setString(3, textHash);
			ps.setInt(4, vector.length);
			ps.setBytes(5, Vectors.toBytes(vector));
			ps.setTimestamp(6, Timestamp.from(now), (Calendar) UTC.clone());
			return ps;
		});
	}

	/** What is stored for these jobs under this model. */
	public Map<String, StoredVector> jobVectors(String modelKey, Collection<String> jobIds) {
		Map<String, StoredVector> found = new HashMap<>();
		for (List<String> chunk : chunks(jobIds)) {
			namedJdbc.query("SELECT job_id, text_hash, vector FROM job_embeddings WHERE model_key = :model AND job_id IN (:ids)",
					new MapSqlParameterSource("model", modelKey).addValue("ids", chunk),
					rs -> {
						found.put(rs.getString("job_id"),
								new StoredVector(rs.getString("text_hash"), Vectors.fromBytes(rs.getBytes("vector"))));
					});
		}
		return found;
	}

	public void saveJobVectors(String modelKey, List<NewJobVector> vectors, Instant now) {
		if (vectors.isEmpty()) {
			return;
		}
		// a fixed order keeps two instances writing the same jobs from deadlocking on the unique key
		List<NewJobVector> sorted = vectors.stream().sorted(Comparator.comparing(NewJobVector::jobId)).toList();
		jdbc.batchUpdate(UPSERT_JOB, sorted, sorted.size(), (ps, v) -> {
			ps.setString(1, UuidV7.at(now));
			ps.setString(2, v.jobId());
			ps.setString(3, modelKey);
			ps.setString(4, v.textHash());
			ps.setInt(5, v.vector().length);
			ps.setBytes(6, Vectors.toBytes(v.vector()));
			if (v.postedAt() == null) {
				ps.setNull(7, Types.TIMESTAMP);
			}
			else {
				ps.setTimestamp(7, Timestamp.from(v.postedAt()), (Calendar) UTC.clone());
			}
			ps.setTimestamp(8, Timestamp.from(now), (Calendar) UTC.clone());
		});
	}

	/**
	 * One page of a model's job vectors changed after (afterAt, afterJobId), for jobs posted since
	 * {@code postedSince} (or without a date), in change order. Keyset paging on (updated_at,
	 * job_id): a batch upsert gives many rows the same updated_at, so the time alone can't page.
	 *
	 * @return rows read
	 */
	public int forEachJobVector(String modelKey, Instant afterAt, String afterJobId, Instant postedSince, int limit,
			JobVectorRow consumer) {
		int[] rows = { 0 };
		namedJdbc.query("""
				SELECT job_id, vector, posted_at, updated_at FROM job_embeddings
				WHERE model_key = :model AND (updated_at > :after OR (updated_at = :after AND job_id > :afterId))
				  AND (posted_at IS NULL OR posted_at >= :since)
				ORDER BY updated_at, job_id LIMIT :limit""",
				new MapSqlParameterSource("model", modelKey)
						.addValue("after", utc(afterAt))
						.addValue("afterId", afterJobId == null ? "" : afterJobId)
						.addValue("since", utc(postedSince))
						.addValue("limit", limit),
				rs -> {
					rows[0]++;
					Timestamp posted = rs.getTimestamp("posted_at", (Calendar) UTC.clone());
					consumer.accept(rs.getString("job_id"), Vectors.fromBytes(rs.getBytes("vector")),
							posted == null ? null : posted.toInstant(),
							rs.getTimestamp("updated_at", (Calendar) UTC.clone()).toInstant());
				});
		return rows[0];
	}

	public long countJobVectors(String modelKey) {
		Long count = jdbc.queryForObject("SELECT COUNT(*) FROM job_embeddings WHERE model_key = ?", Long.class, modelKey);
		return count == null ? 0 : count;
	}

	/** Rows by model, for the admin screen. */
	public Map<String, Long> countsByModel() {
		Map<String, Long> counts = new HashMap<>();
		jdbc.query("SELECT model_key, COUNT(*) AS n FROM job_embeddings GROUP BY model_key",
				rs -> {
					counts.put(rs.getString("model_key"), rs.getLong("n"));
				});
		return counts;
	}

	/** Times are stored in UTC; named parameters can't take a Calendar, so they go as UTC wall-clock time. */
	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static List<List<String>> chunks(Collection<String> values) {
		List<String> all = List.copyOf(values);
		if (all.isEmpty()) {
			return List.of();
		}
		List<List<String>> chunks = new ArrayList<>();
		for (int i = 0; i < all.size(); i += CHUNK) {
			chunks.add(all.subList(i, Math.min(all.size(), i + CHUNK)));
		}
		return chunks;
	}

	public record StoredVector(String textHash, float[] vector) {
	}

	public record NewJobVector(String jobId, String textHash, float[] vector, Instant postedAt) {
	}

	@FunctionalInterface
	public interface JobVectorRow {

		void accept(String jobId, float[] vector, Instant postedAt, Instant updatedAt);

	}

}
