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
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "jobs",
		uniqueConstraints = @UniqueConstraint(name = "uk_jobs_source_external", columnNames = { "source_code", "external_id" }),
		indexes = @Index(name = "idx_jobs_posted_at", columnList = "posted_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Job {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	@Column(name = "source_code", nullable = false, length = 40, updatable = false)
	private String sourceCode;

	/** The board's own id for the posting; together with sourceCode it identifies the job. */
	@Column(name = "external_id", nullable = false, length = 200, updatable = false)
	private String externalId;

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

	@Column(nullable = false, length = 1000)
	private String applyUrl;

	@Column(columnDefinition = "mediumtext")
	private String description;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private JobStatus status = JobStatus.ACTIVE;

	/** When we first saw the posting. */
	@Column(nullable = false, updatable = false)
	private Instant fetchedAt;

	/** When a fetch last returned it. */
	@Column(nullable = false)
	private Instant lastSeenAt;

	@Version
	private long version;

	public Job(String sourceCode, String externalId, Instant fetchedAt) {
		this.sourceCode = sourceCode;
		this.externalId = externalId;
		this.fetchedAt = fetchedAt;
		this.lastSeenAt = fetchedAt;
	}

	/** Copies the latest values from the board. A job seen again is active again. */
	public void refresh(String title, String company, String location, boolean remote, Long salaryMin,
			Long salaryMax, String currency, Instant postedAt, String applyUrl, String description, Instant seenAt) {
		this.title = title;
		this.company = company;
		this.location = location;
		this.remote = remote;
		this.salaryMin = salaryMin;
		this.salaryMax = salaryMax;
		this.currency = currency;
		// keep the first known posting date if the board stops sending one
		if (postedAt != null) {
			this.postedAt = postedAt;
		}
		this.applyUrl = applyUrl;
		this.description = description;
		this.status = JobStatus.ACTIVE;
		this.lastSeenAt = seenAt;
	}

}
