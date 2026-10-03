package com.naukriradar.job.dto.response;

import java.util.List;

/**
 * What a run would produce, without saving anything.
 *
 * @param sample the first few jobs as they would be stored
 * @param problems why items were skipped, one line each (capped)
 */
public record DryRunResponse(
		String sourceCode,
		boolean ok,
		String error,
		int pagesFetched,
		int received,
		int valid,
		int skipped,
		List<JobPreview> sample,
		List<String> problems) {
}
