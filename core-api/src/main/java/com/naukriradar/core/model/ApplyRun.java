package com.naukriradar.core.model;

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

/** One "turn my matches into applications" run. */
@Entity
@Table(name = "apply_runs", indexes = @Index(name = "idx_apply_runs_user", columnList = "user_id, started_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApplyRun {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	@Column(name = "user_id", nullable = false, length = 36, updatable = false)
	private String userId;

	/** The user id while RUNNING, else null; unique, so one running run per user. */
	@Column(name = "running_user_id", unique = true, length = 36)
	private String runningUserId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private ApplyRunStatus status;

	private int matchesConsidered;

	private int queued;

	private int needsYou;

	private int simulated;

	private int failed;

	private int alreadyApplied;

	private int belowScore;

	/** Left for a later run because this one hit its needs-you cap. */
	private int deferred;

	@Column(length = 500)
	private String message;

	@Column(name = "started_at", nullable = false, updatable = false)
	private Instant startedAt;

	private Instant finishedAt;

	@Version
	private long version;

	public ApplyRun(String userId, Instant startedAt) {
		this.userId = userId;
		this.runningUserId = userId;
		this.status = ApplyRunStatus.RUNNING;
		this.startedAt = startedAt;
	}

	public void succeed(int matchesConsidered, int queued, int needsYou, int simulated, int failed, int alreadyApplied,
			int belowScore, int deferred, Instant at) {
		this.matchesConsidered = matchesConsidered;
		this.queued = queued;
		this.needsYou = needsYou;
		this.simulated = simulated;
		this.failed = failed;
		this.alreadyApplied = alreadyApplied;
		this.belowScore = belowScore;
		this.deferred = deferred;
		this.status = ApplyRunStatus.SUCCESS;
		this.message = queued + " sent to the apply engine, " + needsYou + " waiting for you.";
		finish(at);
	}

	public void fail(String reason, Instant at) {
		this.status = ApplyRunStatus.FAILED;
		this.message = reason == null || reason.length() <= 500 ? reason : reason.substring(0, 497) + "...";
		finish(at);
	}

	private void finish(Instant at) {
		this.finishedAt = at;
		this.runningUserId = null;
	}

}
