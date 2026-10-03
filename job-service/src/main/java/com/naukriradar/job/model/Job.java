package com.naukriradar.job.model;

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
 * A stored posting. Rows are written in bulk by {@code JobBatchWriter} with plain JDBC, so
 * the database constraints, not Java checks, decide what counts as a duplicate. The
 * FULLTEXT index on title, company and description is created by {@code SchemaExtras}.
 */
@Entity
@Table(name = "jobs",
		uniqueConstraints = {
				@UniqueConstraint(name = "uk_jobs_source_external", columnNames = { "source_code", "external_id" }),
				@UniqueConstraint(name = "uk_jobs_fingerprint", columnNames = "fingerprint") },
		indexes = {
				@Index(name = "idx_jobs_status_sort", columnList = "status, sort_at, id"),
				@Index(name = "idx_jobs_last_seen", columnList = "last_seen_at") })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "source_code", nullable = false, length = 40, updatable = false)
	private String sourceCode;

	/** The board's own id for the posting; with sourceCode it identifies the job on that board. */
	@Column(name = "external_id", nullable = false, length = 200, updatable = false)
	private String externalId;

	/** Hash of normalised title, company and city; the same job on two boards gets the same value. */
	@Column(nullable = false, length = 64)
	private String fingerprint;

	@Column(nullable = false, length = 300)
	private String title;

	@Column(nullable = false, length = 200)
	private String company;

	@Column(length = 200)
	private String location;

	private boolean remote;

	private Long salaryMin;

	private Long salaryMax;

	@Column(length = 3)
	private String currency;

	@Column(name = "posted_at")
	private Instant postedAt;

	/** postedAt, or fetchedAt when the board gave no date. Never null, so it can drive paging. */
	@Column(name = "sort_at", nullable = false)
	private Instant sortAt;

	@Column(nullable = false, length = 1000)
	private String applyUrl;

	@Column(columnDefinition = "mediumtext")
	private String description;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private JobStatus status;

	/** When we first saw the posting. */
	@Column(nullable = false, updatable = false)
	private Instant fetchedAt;

	/** When a fetch last returned it. */
	@Column(name = "last_seen_at", nullable = false)
	private Instant lastSeenAt;

	@Version
	private long version;

}
