package com.naukriradar.job.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import com.naukriradar.job.model.JobStatus;
import com.naukriradar.job.normalizer.NormalizedJob;
import com.naukriradar.job.util.UuidV7;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves a source's jobs in batches. Jobs the source already has are updated; the rest are
 * inserted with {@code INSERT IGNORE}, so the unique keys decide what is a duplicate. That
 * stays correct even when two writers race, where a "check, then insert" would not.
 *
 * <p>A job already stored from another board (same fingerprint) is ignored, not merged:
 * the first board to list it keeps it.
 */
@Repository
public class JobBatchWriter {

	private static final int BATCH_SIZE = 500;

	private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

	private static final String FIND_EXISTING = """
			SELECT external_id FROM jobs WHERE source_code = :sourceCode AND external_id IN (:externalIds)
			""";

	// SET runs left to right in MySQL, so sort_at sees the new posted_at.
	// IGNORE: if new values would collide with another job's fingerprint, leave the row as is.
	private static final String UPDATE = """
			UPDATE IGNORE jobs SET
			  fingerprint = ?, title = ?, company = ?, location = ?, remote = ?,
			  salary_min = ?, salary_max = ?, currency = ?,
			  posted_at = COALESCE(?, posted_at), sort_at = COALESCE(posted_at, fetched_at),
			  apply_url = ?, description = ?, status = ?, last_seen_at = ?, version = version + 1
			WHERE source_code = ? AND external_id = ?
			""";

	private static final String INSERT = """
			INSERT IGNORE INTO jobs
			  (id, source_code, external_id, fingerprint, title, company, location, remote,
			   salary_min, salary_max, currency, posted_at, sort_at, apply_url, description,
			   status, fetched_at, last_seen_at, version)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
			""";

	private final JdbcTemplate jdbc;
	private final NamedParameterJdbcTemplate namedJdbc;

	public JobBatchWriter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
	}

	/**
	 * Jobs must already be unique by external id (case-insensitively), as the ingest run makes
	 * them. Rows are written in fingerprint order: two sources saving the same posting at once
	 * then take their index locks in the same order, which keeps InnoDB from deadlocking them.
	 */
	@Transactional
	public WriteCounts write(String sourceCode, List<FingerprintedJob> unsorted, Instant now) {
		List<FingerprintedJob> jobs = unsorted.stream()
				.sorted(Comparator.comparing(FingerprintedJob::fingerprint))
				.toList();
		int inserted = 0;
		int updated = 0;
		int duplicates = 0;
		for (int from = 0; from < jobs.size(); from += BATCH_SIZE) {
			List<FingerprintedJob> batch = jobs.subList(from, Math.min(from + BATCH_SIZE, jobs.size()));
			Set<String> known = existingIds(sourceCode, batch);

			List<FingerprintedJob> toUpdate = new ArrayList<>();
			List<FingerprintedJob> toInsert = new ArrayList<>();
			for (FingerprintedJob job : batch) {
				(known.contains(job.job().externalId().toLowerCase(Locale.ROOT)) ? toUpdate : toInsert).add(job);
			}

			for (int count : batchUpdate(UPDATE, toUpdate, (ps, job) -> bindUpdate(ps, sourceCode, job, now))) {
				if (count > 0) {
					updated++;
				}
				else {
					duplicates++;
				}
			}
			for (int count : batchUpdate(INSERT, toInsert, (ps, job) -> bindInsert(ps, sourceCode, job, now))) {
				if (count > 0) {
					inserted++;
				}
				else {
					duplicates++;
				}
			}
		}
		return new WriteCounts(inserted, updated, duplicates);
	}

	private Set<String> existingIds(String sourceCode, List<FingerprintedJob> batch) {
		List<String> ids = batch.stream().map(job -> job.job().externalId()).toList();
		Set<String> known = new HashSet<>();
		// MySQL matches case-insensitively, so compare in lower case on our side too
		namedJdbc.queryForList(FIND_EXISTING, Map.of("sourceCode", sourceCode, "externalIds", ids), String.class)
				.forEach(id -> known.add(id.toLowerCase(Locale.ROOT)));
		return known;
	}

	private int[] batchUpdate(String sql, List<FingerprintedJob> jobs, Binder binder) {
		if (jobs.isEmpty()) {
			return new int[0];
		}
		int[][] counts = jdbc.batchUpdate(sql, jobs, jobs.size(), binder::bind);
		return counts.length == 0 ? new int[0] : counts[0];
	}

	private static void bindUpdate(PreparedStatement ps, String sourceCode, FingerprintedJob fingerprinted, Instant now)
			throws SQLException {
		NormalizedJob job = fingerprinted.job();
		int i = 1;
		ps.setString(i++, fingerprinted.fingerprint());
		ps.setString(i++, job.title());
		ps.setString(i++, job.company());
		ps.setString(i++, job.location());
		ps.setBoolean(i++, job.remote());
		setLong(ps, i++, job.salaryMin());
		setLong(ps, i++, job.salaryMax());
		ps.setString(i++, job.currency());
		setInstant(ps, i++, job.postedAt());
		ps.setString(i++, job.applyUrl());
		ps.setString(i++, job.description());
		ps.setString(i++, JobStatus.ACTIVE.name());
		setInstant(ps, i++, now);
		ps.setString(i++, sourceCode);
		ps.setString(i, job.externalId());
	}

	private static void bindInsert(PreparedStatement ps, String sourceCode, FingerprintedJob fingerprinted, Instant now)
			throws SQLException {
		NormalizedJob job = fingerprinted.job();
		int i = 1;
		ps.setString(i++, UuidV7.generate());
		ps.setString(i++, sourceCode);
		ps.setString(i++, job.externalId());
		ps.setString(i++, fingerprinted.fingerprint());
		ps.setString(i++, job.title());
		ps.setString(i++, job.company());
		ps.setString(i++, job.location());
		ps.setBoolean(i++, job.remote());
		setLong(ps, i++, job.salaryMin());
		setLong(ps, i++, job.salaryMax());
		ps.setString(i++, job.currency());
		setInstant(ps, i++, job.postedAt());
		setInstant(ps, i++, job.postedAt() != null ? job.postedAt() : now);
		ps.setString(i++, job.applyUrl());
		ps.setString(i++, job.description());
		ps.setString(i++, JobStatus.ACTIVE.name());
		setInstant(ps, i++, now);
		setInstant(ps, i, now);
	}

	private static void setLong(PreparedStatement ps, int index, Long value) throws SQLException {
		if (value == null) {
			ps.setNull(index, Types.BIGINT);
		}
		else {
			ps.setLong(index, value);
		}
	}

	/** Stored as UTC, the same way Hibernate is told to store Instants. */
	static void setInstant(PreparedStatement ps, int index, Instant value) throws SQLException {
		if (value == null) {
			ps.setNull(index, Types.TIMESTAMP);
		}
		else {
			ps.setTimestamp(index, Timestamp.from(value), (Calendar) UTC.clone());
		}
	}

	public record FingerprintedJob(NormalizedJob job, String fingerprint) {
	}

	public record WriteCounts(int inserted, int updated, int duplicates) {
	}

	@FunctionalInterface
	private interface Binder {

		void bind(PreparedStatement ps, FingerprintedJob job) throws SQLException;

	}

}
