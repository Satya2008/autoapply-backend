package com.naukriradar.job.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * Query parameters for GET /api/v1/jobs. Everything is optional.
 *
 * @param q words that must all appear in the title, company or description
 * @param cursor the {@code nextCursor} from the previous page
 */
public record JobSearchRequest(
		@Size(max = 200) String q,
		@Size(max = 100) String location,
		Boolean remote,
		@Size(max = 40) String source,
		@Size(max = 10) String region,
		@Min(1) @Max(365) Integer postedWithinDays,
		@Size(max = 200) String cursor,
		@Min(1) @Max(100) Integer limit) {

	public static final int DEFAULT_LIMIT = 20;

	/** The same search without a source filter (users may not pick sources). */
	public JobSearchRequest withoutSource() {
		return new JobSearchRequest(q, location, remote, null, region, postedWithinDays, cursor, limit);
	}

	public int limitOrDefault() {
		return limit == null ? DEFAULT_LIMIT : limit;
	}

}
