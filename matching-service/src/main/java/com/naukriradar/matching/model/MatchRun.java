package com.naukriradar.matching.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "match_runs", indexes = @Index(name = "idx_match_runs_user", columnList = "user_id, started_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchRun {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	@Column(name = "user_id", nullable = false, length = 36, updatable = false)
	private String userId;

	/**
	 * The user id while the run is RUNNING, null otherwise. It is unique, and MySQL allows
	 * many NULLs in a unique column, so the database itself refuses a second running run
	 * for the same user, even if two requests arrive together.
	 */
	@Column(name = "running_user_id", unique = true, length = 36)
	private String runningUserId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private MatchRunStatus status;

	private int jobsConsidered;

	private int excluded;

	private int matchesCreated;

	private int matchesUpdated;

	/** Scored under the store threshold; any earlier match for these jobs was removed. */
	private int belowThreshold;

	@Column(name = "ai_reviewed", nullable = false)
	private int aiReviewed;

	@Column(length = 500)
	private String message;

	@Column(name = "started_at", nullable = false, updatable = false)
	private Instant startedAt;

	private Instant finishedAt;

	@Version
	private long version;

	public MatchRun(String userId, Instant startedAt) {
		this.userId = userId;
		this.runningUserId = userId;
		this.status = MatchRunStatus.RUNNING;
		this.startedAt = startedAt;
	}

	public void succeed(int jobsConsidered, int excluded, int created, int updated, int belowThreshold, int aiReviewed,
			String aiNote, Instant at) {
		this.jobsConsidered = jobsConsidered;
		this.excluded = excluded;
		this.matchesCreated = created;
		this.matchesUpdated = updated;
		this.belowThreshold = belowThreshold;
		this.aiReviewed = aiReviewed;
		this.status = MatchRunStatus.SUCCESS;
		String message = "Scored " + jobsConsidered + " jobs: " + created + " new matches, " + updated + " updated."
				+ (aiReviewed > 0 ? " AI reviewed the top " + aiReviewed + "." : "") + (aiNote == null ? "" : " " + aiNote);
		this.message = message.length() <= 500 ? message : message.substring(0, 497) + "...";
		finish(at);
	}

	public void fail(String reason, Instant at) {
		this.status = MatchRunStatus.FAILED;
		this.message = reason == null || reason.length() <= 500 ? reason : reason.substring(0, 497) + "...";
		finish(at);
	}

	private void finish(Instant at) {
		this.finishedAt = at;
		this.runningUserId = null;
	}

}
