package com.naukriradar.matching.repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.model.MatchStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saves a run's matches in one batch with an upsert: a new (user, job) pair is inserted, an
 * existing one gets the new score and job details but keeps its id, status and created time.
 * Running matching twice therefore never duplicates a match, it refreshes it.
 *
 * <p>The datasource uses useAffectedRows, so MySQL reports 1 for an insert and 2 for an
 * update, which is how new and refreshed matches are counted.
 */
@Repository
public class JobMatchWriter {

	private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

	// row alias syntax ("AS new") replaces the deprecated VALUES() function
	private static final String UPSERT = """
			INSERT INTO job_matches
			  (id, user_id, job_id, score, breakdown, job_title, job_company, job_location, job_remote,
			   job_posted_at, job_apply_url, status, created_at, updated_at, version)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0) AS new
			ON DUPLICATE KEY UPDATE
			  score = new.score, breakdown = new.breakdown, job_title = new.job_title,
			  job_company = new.job_company, job_location = new.job_location, job_remote = new.job_remote,
			  job_posted_at = new.job_posted_at, job_apply_url = new.job_apply_url,
			  updated_at = new.updated_at, version = job_matches.version + 1
			""";

	private final JdbcTemplate jdbc;
	private final NamedParameterJdbcTemplate namedJdbc;

	public JobMatchWriter(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		this.namedJdbc = new NamedParameterJdbcTemplate(jdbc);
	}

	@Transactional
	public Counts upsert(String userId, List<ScoredJob> unsorted, Instant now) {
		if (unsorted.isEmpty()) {
			return new Counts(0, 0);
		}
		// a fixed order keeps two writers for the same user from deadlocking on the unique key
		List<ScoredJob> matches = unsorted.stream().sorted(Comparator.comparing(m -> m.job().id())).toList();
		int[][] results = jdbc.batchUpdate(UPSERT, matches, matches.size(), (ps, match) -> bind(ps, userId, match, now));
		int created = 0;
		int updated = 0;
		for (int count : results[0]) {
			if (count == 1) {
				created++;
			}
			else if (count == 2) {
				updated++;
			}
		}
		return new Counts(created, updated);
	}

	/** Removes matches for jobs that now score below the threshold, so stale high scores don't linger. */
	@Transactional
	public int deleteFor(String userId, Collection<String> jobIds) {
		if (jobIds.isEmpty()) {
			return 0;
		}
		return namedJdbc.update("DELETE FROM job_matches WHERE user_id = :userId AND job_id IN (:jobIds)",
				Map.of("userId", userId, "jobIds", jobIds));
	}

	/** The AI review of one match; local score and the rest stay as they are. */
	public void saveAiScore(String userId, String jobId, int score, String reasonsJson, String scoredBy, Instant at) {
		jdbc.update(con -> {
			PreparedStatement ps = con.prepareStatement("UPDATE job_matches SET ai_score = ?, ai_reasons = ?, ai_scored_by = ?,"
					+ " ai_scored_at = ? WHERE user_id = ? AND job_id = ?");
			ps.setInt(1, score);
			ps.setString(2, reasonsJson);
			ps.setString(3, truncate(scoredBy, 160));
			setInstant(ps, 4, at);
			ps.setString(5, userId);
			ps.setString(6, jobId);
			return ps;
		});
	}

	private static void bind(PreparedStatement ps, String userId, ScoredJob match, Instant now) throws SQLException {
		CandidateJob job = match.job();
		int i = 1;
		ps.setString(i++, uuidV7(now));
		ps.setString(i++, userId);
		ps.setString(i++, job.id());
		ps.setInt(i++, match.score());
		ps.setString(i++, match.breakdownJson());
		ps.setString(i++, truncate(job.title(), 300));
		ps.setString(i++, truncate(job.company(), 200));
		ps.setString(i++, truncate(job.location(), 200));
		ps.setBoolean(i++, job.remote());
		setInstant(ps, i++, job.postedAt());
		ps.setString(i++, job.applyUrl());
		ps.setString(i++, MatchStatus.NEW.name());
		setInstant(ps, i++, now);
		setInstant(ps, i, now);
	}

	private static void setInstant(PreparedStatement ps, int index, Instant value) throws SQLException {
		if (value == null) {
			ps.setNull(index, Types.TIMESTAMP);
		}
		else {
			ps.setTimestamp(index, Timestamp.from(value), (Calendar) UTC.clone());
		}
	}

	private static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

	/** Time-ordered UUID (version 7), matching what Hibernate generates for entities. */
	private static String uuidV7(Instant at) {
		long random = UUID.randomUUID().getLeastSignificantBits();
		long mostSig = (at.toEpochMilli() << 16) | (0x7L << 12) | (random & 0xFFF);
		long leastSig = 0x8000_0000_0000_0000L | (UUID.randomUUID().getMostSignificantBits() & 0x3FFF_FFFF_FFFF_FFFFL);
		return new UUID(mostSig, leastSig).toString();
	}

	public record ScoredJob(CandidateJob job, int score, String breakdownJson) {
	}

	public record Counts(int created, int updated) {
	}

}
