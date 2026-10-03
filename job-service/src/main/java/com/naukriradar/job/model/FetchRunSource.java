package com.naukriradar.job.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** How one source did within a fetch run. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class FetchRunSource {

	@Column(nullable = false, length = 40)
	private String sourceCode;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private RunStatus status;

	@Column(length = 500)
	private String message;

	private int received;

	private int inserted;

	private int updated;

	/** Already stored from another board (same fingerprint). */
	private int duplicates;

	private int skipped;

	private long durationMs;

}
