package com.naukriradar.job.dto.request;

import java.util.List;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Asks for the active jobs most relevant to a set of words (a candidate's skills and target
 * roles). Used by matching-service to shortlist before scoring.
 */
public record CandidateQuery(
		@NotNull @Size(max = 100) List<@NotNull @Size(max = 100) String> keywords,
		@Min(1) @Max(365) Integer postedWithinDays,
		@Min(1) @Max(1000) Integer limit) {

	public static final int DEFAULT_LIMIT = 300;

	public static final int DEFAULT_DAYS = 60;

	public int limitOrDefault() {
		return limit == null ? DEFAULT_LIMIT : limit;
	}

	public int daysOrDefault() {
		return postedWithinDays == null ? DEFAULT_DAYS : postedWithinDays;
	}

}
