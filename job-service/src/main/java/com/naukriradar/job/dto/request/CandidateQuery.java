package com.naukriradar.job.dto.request;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Asks for the active jobs most relevant to a set of words (a candidate's skills and target
 * roles). Used by matching-service to shortlist before scoring.
 *
 * @param required the job must mention at least one of these words (the candidate's "must be
 *     about"); null or empty for no rule
 * @param indiaOrRemote only jobs located in India, or remote ones
 */
public record CandidateQuery(
		@NotNull @Size(max = 100) List<@NotNull @Size(max = 100) String> keywords,
		@Size(max = 20) List<@NotNull @Size(max = 50) String> required,
		Boolean indiaOrRemote,
		@Min(1) @Max(365) Integer postedWithinDays,
		@Min(1) @Max(1000) Integer limit) {

	public static final int DEFAULT_LIMIT = 300;

	public static final int DEFAULT_DAYS = 60;

	/** Without the job filters. */
	public CandidateQuery(List<String> keywords, Integer postedWithinDays, Integer limit) {
		this(keywords, null, null, postedWithinDays, limit);
	}

	public int limitOrDefault() {
		return limit == null ? DEFAULT_LIMIT : limit;
	}

	public int daysOrDefault() {
		return postedWithinDays == null ? DEFAULT_DAYS : postedWithinDays;
	}

	public List<String> requiredOrNone() {
		return required == null ? List.of() : required;
	}

	public boolean indiaOrRemoteOnly() {
		return Boolean.TRUE.equals(indiaOrRemote);
	}

}
