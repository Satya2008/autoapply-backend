package com.naukriradar.job.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * A job board, described entirely as data: where to call, what to send, and where each job
 * field sits in the response. Adding a board means adding a row, not code.
 */
@Entity
@Table(name = "job_sources")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobSource {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(length = 36)
	private String id;

	/** Short stable name, stored on every job from this source. Never changes. */
	@Setter(AccessLevel.NONE)
	@Column(nullable = false, unique = true, length = 40, updatable = false)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private SourceType type;

	@Column(nullable = false, length = 500)
	private String baseUrl;

	@Column(length = 500)
	private String searchPath;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private RequestMethod method;

	/** JSON body for POST sources; {query} and {page} are filled in. */
	@Column(columnDefinition = "text")
	private String bodyTemplate;

	/** Values may use ${setting:key} so secrets never sit in this table. */
	@ElementCollection
	@CollectionTable(name = "job_source_headers", joinColumns = @JoinColumn(name = "source_id"))
	@MapKeyColumn(name = "name", length = 100)
	@Column(name = "value", nullable = false, length = 1000)
	private Map<String, String> headers = new LinkedHashMap<>();

	@ElementCollection
	@CollectionTable(name = "job_source_query_params", joinColumns = @JoinColumn(name = "source_id"))
	@MapKeyColumn(name = "name", length = 100)
	@Column(name = "value", nullable = false, length = 500)
	private Map<String, String> queryParams = new LinkedHashMap<>();

	/** JsonPath to the array of jobs in the response, e.g. {@code $.data}. */
	@Column(nullable = false, length = 300)
	private String resultsPath;

	/** {@link JobField} key to JsonPath, evaluated against each item of the results array. */
	@ElementCollection
	@CollectionTable(name = "job_source_field_mappings", joinColumns = @JoinColumn(name = "source_id"))
	@MapKeyColumn(name = "field", length = 30)
	@Column(name = "json_path", nullable = false, length = 300)
	private Map<String, String> fieldMappings = new LinkedHashMap<>();

	private boolean enabled;

	/** Higher runs first when several sources are fetched. */
	private int priority;

	private int timeoutSeconds;

	/** Pages requested per run; fetching stops early when a page comes back empty. */
	private int maxPages;

	private Instant lastRunAt;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private RunStatus lastRunStatus = RunStatus.NEVER;

	@Column(length = 500)
	private String lastRunMessage;

	private int consecutiveFailures;

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	@Version
	private long version;

	public JobSource(String code) {
		this.code = code;
	}

	public void recordSuccess(Instant at, String message) {
		lastRunAt = at;
		lastRunStatus = RunStatus.SUCCESS;
		lastRunMessage = truncate(message);
		consecutiveFailures = 0;
	}

	/**
	 * Records a failed run and disables the source once it has failed {@code disableAfter}
	 * times in a row, so a dead board stops being hammered. Returns true if it was disabled now.
	 */
	public boolean recordFailure(Instant at, String message, int disableAfter) {
		lastRunAt = at;
		lastRunStatus = RunStatus.FAILED;
		lastRunMessage = truncate(message);
		consecutiveFailures++;
		if (enabled && disableAfter > 0 && consecutiveFailures >= disableAfter) {
			enabled = false;
			return true;
		}
		return false;
	}

	private static String truncate(String message) {
		if (message == null || message.length() <= 500) {
			return message;
		}
		return message.substring(0, 497) + "...";
	}

}
