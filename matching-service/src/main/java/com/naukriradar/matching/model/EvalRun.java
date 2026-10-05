package com.naukriradar.matching.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * One eval over the golden set, with its numbers. A passed PROMPT run is what lets that prompt
 * version be activated.
 */
@Entity
@Table(name = "eval_runs", indexes = @Index(name = "ix_eval_runs_prompt", columnList = "prompt_code, prompt_version, status"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvalRun {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private EvalKind kind;

	@Column(name = "prompt_code", length = 50)
	private String promptCode;

	@Column(name = "prompt_version")
	private Integer promptVersion;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private EvalRunStatus status;

	/** Null until it finishes. */
	private Boolean passed;

	@Column(name = "case_count", nullable = false)
	private int caseCount;

	/** The numbers and the per-case results, as JSON. */
	@Column(name = "metrics_json", columnDefinition = "MEDIUMTEXT")
	private String metricsJson;

	@Column(length = 500)
	private String error;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	public EvalRun(EvalKind kind, String promptCode, Integer promptVersion, int caseCount, Instant createdAt) {
		this.kind = kind;
		this.promptCode = promptCode;
		this.promptVersion = promptVersion;
		this.caseCount = caseCount;
		this.status = EvalRunStatus.QUEUED;
		this.createdAt = createdAt;
	}

	public void start() {
		this.status = EvalRunStatus.RUNNING;
	}

	public void succeed(boolean passed, String metricsJson, Instant at) {
		this.status = EvalRunStatus.SUCCEEDED;
		this.passed = passed;
		this.metricsJson = metricsJson;
		this.finishedAt = at;
	}

	public void fail(String error, Instant at) {
		this.status = EvalRunStatus.FAILED;
		this.passed = false;
		this.error = error == null || error.length() <= 500 ? error : error.substring(0, 500);
		this.finishedAt = at;
	}

}
