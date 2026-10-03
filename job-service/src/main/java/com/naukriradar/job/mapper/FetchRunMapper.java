package com.naukriradar.job.mapper;

import java.util.List;

import com.naukriradar.job.dto.response.FetchResultResponse;
import com.naukriradar.job.dto.response.FetchRunResponse;
import com.naukriradar.job.model.FetchRun;
import com.naukriradar.job.model.FetchRunSource;
import com.naukriradar.job.model.RunStatus;
import org.springframework.stereotype.Component;

@Component
public class FetchRunMapper {

	public FetchRunSource toSource(FetchResultResponse result) {
		return new FetchRunSource(result.sourceCode(), result.status(), truncate(result.message()), result.received(),
				result.inserted(), result.updated(), result.duplicates(), result.skipped(), result.durationMs());
	}

	public FetchRunResponse toResponse(FetchRun run, boolean withSources) {
		List<FetchRunResponse.SourceResult> sources = withSources ? run.getSources().stream()
				.map(s -> new FetchRunResponse.SourceResult(s.getSourceCode(), s.getStatus(), s.getMessage(),
						s.getReceived(), s.getInserted(), s.getUpdated(), s.getDuplicates(), s.getSkipped(),
						s.getDurationMs()))
				.toList() : null;
		int failed = (int) run.getSources().stream().filter(s -> s.getStatus() == RunStatus.FAILED).count();
		return new FetchRunResponse(run.getId(), run.getTrigger(), run.getStatus(), run.getStartedAt(),
				run.getFinishedAt(), run.getMessage(), run.getSources().size() - failed, failed,
				run.total(FetchRunSource::getReceived), run.total(FetchRunSource::getInserted),
				run.total(FetchRunSource::getUpdated), run.total(FetchRunSource::getDuplicates),
				run.total(FetchRunSource::getSkipped), sources);
	}

	private static String truncate(String message) {
		return message == null || message.length() <= 500 ? message : message.substring(0, 497) + "...";
	}

}
