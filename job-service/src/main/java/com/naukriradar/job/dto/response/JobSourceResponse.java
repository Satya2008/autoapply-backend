package com.naukriradar.job.dto.response;

import java.time.Instant;
import java.util.Map;

import com.naukriradar.job.model.RequestMethod;
import com.naukriradar.job.model.RunStatus;
import com.naukriradar.job.model.SourceType;

/** Header values typed in literally are masked; ${setting:...} references are shown as is. */
public record JobSourceResponse(
		String id,
		String code,
		String name,
		SourceType type,
		String baseUrl,
		String searchPath,
		RequestMethod method,
		String bodyTemplate,
		Map<String, String> headers,
		Map<String, String> queryParams,
		String resultsPath,
		Map<String, String> fieldMappings,
		boolean enabled,
		int priority,
		int timeoutSeconds,
		int maxPages,
		Instant lastRunAt,
		RunStatus lastRunStatus,
		String lastRunMessage,
		int consecutiveFailures,
		long jobCount) {
}
