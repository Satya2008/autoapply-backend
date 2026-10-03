package com.naukriradar.job.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/** One "fetch every enabled board" run, with totals and a line per source. */
@Entity
@Table(name = "fetch_runs", indexes = @Index(name = "idx_fetch_runs_started", columnList = "started_at"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FetchRun {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	// "trigger" is a reserved word in MySQL
	@Column(name = "run_trigger", nullable = false, length = 10, updatable = false)
	private RunTrigger trigger;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private FetchRunStatus status;

	@Column(name = "started_at", nullable = false, updatable = false)
	private Instant startedAt;

	private Instant finishedAt;

	@Column(length = 500)
	private String message;

	@ElementCollection
	@CollectionTable(name = "fetch_run_sources", joinColumns = @JoinColumn(name = "run_id"))
	@OrderColumn(name = "seq")
	private List<FetchRunSource> sources = new ArrayList<>();

	@Version
	private long version;

	public FetchRun(RunTrigger trigger, Instant startedAt) {
		this.trigger = trigger;
		this.startedAt = startedAt;
		this.status = FetchRunStatus.RUNNING;
	}

	public void addSource(FetchRunSource source) {
		sources.add(source);
	}

	/** Works out the overall status from the sources once all of them are done. */
	public void finish(Instant at) {
		long failed = sources.stream().filter(s -> s.getStatus() == RunStatus.FAILED).count();
		if (sources.isEmpty()) {
			status = FetchRunStatus.SUCCESS;
			message = "No enabled sources.";
		}
		else if (failed == 0) {
			status = FetchRunStatus.SUCCESS;
		}
		else if (failed < sources.size()) {
			status = FetchRunStatus.PARTIAL;
		}
		else {
			status = FetchRunStatus.FAILED;
		}
		finishedAt = at;
	}

	public void fail(Instant at, String reason) {
		status = FetchRunStatus.FAILED;
		finishedAt = at;
		message = reason != null && reason.length() > 500 ? reason.substring(0, 497) + "..." : reason;
	}

	public int total(ToIntFunction<FetchRunSource> field) {
		return sources.stream().mapToInt(field).sum();
	}

}
