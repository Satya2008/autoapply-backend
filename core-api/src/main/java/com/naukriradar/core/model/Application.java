package com.naukriradar.core.model;

import java.time.Instant;

import com.naukriradar.core.exception.IllegalTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * One job a user is applying to. The status only changes through
 * {@code ApplicationStateMachine}, which checks the move and records it as an event.
 */
@Entity
@Table(name = "applications",
		uniqueConstraints = @UniqueConstraint(name = "uk_applications_user_job", columnNames = { "user_id", "job_id" }),
		indexes = @Index(name = "idx_applications_user_status", columnList = "user_id, status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Application {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	@Column(name = "user_id", nullable = false, length = 36, updatable = false)
	private String userId;

	@Column(name = "job_id", nullable = false, length = 36, updatable = false)
	private String jobId;

	@Column(nullable = false, length = 300)
	private String jobTitle;

	@Column(nullable = false, length = 200)
	private String jobCompany;

	@Column(length = 200)
	private String jobLocation;

	@Column(nullable = false, length = 1000)
	private String applyUrl;

	private int matchScore;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 12)
	private ApplicationStatus status;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private RiskBand riskBand;

	/** Why the risk band is what it is, e.g. "linkedin.com bans automated applications". */
	@Column(length = 300)
	private String riskReason;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(length = 10)
	private SubmittedVia submittedVia;

	/** Why the user has to apply themselves, shown on the "needs you" list. */
	@Column(length = 300)
	private String needsYouReason;

	/** Counted against the daily limit: it went down the automatic path. */
	private boolean automated;

	/** Ready answers for the application form, as JSON. */
	@Column(columnDefinition = "text")
	private String prefill;

	private int attempts;

	private Instant nextAttemptAt;

	@Column(length = 500)
	private String lastError;

	/** Written by AI on request; the candidate can regenerate it. */
	@Column(name = "cover_letter", columnDefinition = "text")
	private String coverLetter;

	@Column(name = "cover_letter_at")
	private Instant coverLetterAt;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	private Instant updatedAt;

	@Version
	private long version;

	public Application(String userId, String jobId, String jobTitle, String jobCompany, String jobLocation,
			String applyUrl, int matchScore, RiskBand riskBand, String riskReason, String prefill) {
		this.userId = userId;
		this.jobId = jobId;
		this.jobTitle = jobTitle;
		this.jobCompany = jobCompany;
		this.jobLocation = jobLocation;
		this.applyUrl = applyUrl;
		this.matchScore = matchScore;
		this.riskBand = riskBand;
		this.riskReason = riskReason;
		this.prefill = prefill;
		this.status = ApplicationStatus.PLANNED;
	}

	/**
	 * Moves to {@code next} if the status rules allow it, and returns the old status. The
	 * rules live here, on the entity, so no caller can get around them. Use
	 * {@code ApplicationStateMachine}, which also records the move.
	 *
	 * @throws IllegalTransitionException if the move isn't allowed
	 */
	public ApplicationStatus moveTo(ApplicationStatus next) {
		if (!status.canMoveTo(next)) {
			throw new IllegalTransitionException(status, next);
		}
		if (next == ApplicationStatus.QUEUED && riskBand != RiskBand.LOW) {
			// the one rule that protects the candidate's accounts: risky sites are never automated
			throw new IllegalTransitionException("Only low-risk sites can be applied to automatically; this one is "
					+ riskBand + ".");
		}
		ApplicationStatus previous = status;
		this.status = next;
		return previous;
	}

	public void needsYouBecause(String reason) {
		this.needsYouReason = reason == null || reason.length() <= 300 ? reason : reason.substring(0, 297) + "...";
	}

	public void markAutomated() {
		this.automated = true;
	}

	public void submittedVia(SubmittedVia via) {
		this.submittedVia = via;
	}

	public void recordFailure(String error, Instant retryAt) {
		this.attempts++;
		this.lastError = error == null || error.length() <= 500 ? error : error.substring(0, 497) + "...";
		this.nextAttemptAt = retryAt;
	}

	public void coverLetter(String letter, Instant at) {
		this.coverLetter = letter;
		this.coverLetterAt = at;
	}

}
