package com.naukriradar.job.dto.response;

import java.time.Instant;
import java.util.List;

import com.naukriradar.job.model.FetchRunStatus;
import com.naukriradar.job.model.RunStatus;
import com.naukriradar.job.model.RunTrigger;

/** A fetch run with totals; {@code sources} is filled only when one run is requested. */
public record FetchRunResponse(
		String id,
		RunTrigger trigger,
		FetchRunStatus status,
		Instant startedAt,
		Instant finishedAt,
		String message,
		int sourcesSucceeded,
		int sourcesFailed,
		int received,
		int inserted,
		int updated,
		int duplicates,
		int skipped,
		List<SourceResult> sources) {

	public record SourceResult(
			String sourceCode,
			RunStatus status,
			String message,
			int received,
			int inserted,
			int updated,
			int duplicates,
			int skipped,
			long durationMs) {
	}

}
