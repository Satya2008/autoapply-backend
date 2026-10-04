package com.naukriradar.matching.model;

import java.time.Instant;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A job scored for a user. Written by {@code JobMatchWriter} with an upsert, so re-running
 * matching refreshes scores instead of adding rows. The job's title, company and link are
 * copied in, so listing matches needs no call to job-service.
 */
@Entity
@Table(name = "job_matches",
		uniqueConstraints = @UniqueConstraint(name = "uk_job_matches_user_job", columnNames = { "user_id", "job_id" }),
		indexes = @Index(name = "idx_job_matches_user_score", columnList = "user_id, score, id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobMatch {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "user_id", nullable = false, length = 36)
	private String userId;

	@Column(name = "job_id", nullable = false, length = 36)
	private String jobId;

	@Column(nullable = false)
	private int score;

	/** The factor-by-factor breakdown, as JSON. */
	@Column(nullable = false, columnDefinition = "text")
	private String breakdown;

	@Column(nullable = false, length = 300)
	private String jobTitle;

	@Column(nullable = false, length = 200)
	private String jobCompany;

	@Column(length = 200)
	private String jobLocation;

	private boolean jobRemote;

	private Instant jobPostedAt;

	@Column(nullable = false, length = 1000)
	private String jobApplyUrl;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private MatchStatus status;

	/** 0-100 from the AI review of the top matches; null when not reviewed. */
	@Column(name = "ai_score")
	private Integer aiScore;

	/** JSON list of short reasons for the AI score. */
	@Column(name = "ai_reasons", columnDefinition = "text")
	private String aiReasons;

	/** provider:model that gave the AI score. */
	@Column(name = "ai_scored_by", length = 160)
	private String aiScoredBy;

	@Column(name = "ai_scored_at")
	private Instant aiScoredAt;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Column(nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

}
