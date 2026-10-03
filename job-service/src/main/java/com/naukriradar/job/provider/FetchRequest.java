package com.naukriradar.job.provider;

/**
 * One page request to a board.
 *
 * @param query search words, or empty for boards that don't search
 * @param page 1-based page number
 */
public record FetchRequest(String query, int page) {

	public FetchRequest {
		query = query == null ? "" : query.strip();
		if (page < 1) {
			throw new IllegalArgumentException("page starts at 1");
		}
	}

}
