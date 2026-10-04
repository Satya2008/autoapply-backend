package com.naukriradar.matching.mapper;

import java.util.List;

import com.naukriradar.matching.dto.response.MatchDetailResponse;
import com.naukriradar.matching.dto.response.MatchRunResponse;
import com.naukriradar.matching.dto.response.MatchSummaryResponse;
import com.naukriradar.matching.model.JobMatch;
import com.naukriradar.matching.model.MatchRun;
import com.naukriradar.matching.scoring.FactorScore;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MatchMapper {

	private static final TypeReference<List<FactorScore>> BREAKDOWN = new TypeReference<>() {
	};

	private static final TypeReference<List<String>> REASONS = new TypeReference<>() {
	};

	private final JsonMapper jsonMapper;

	public MatchMapper(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public MatchRunResponse toResponse(MatchRun run) {
		return new MatchRunResponse(run.getId(), run.getStatus(), run.getJobsConsidered(), run.getExcluded(),
				run.getMatchesCreated(), run.getMatchesUpdated(), run.getBelowThreshold(), run.getAiReviewed(), run.getMessage(),
				run.getStartedAt(), run.getFinishedAt());
	}

	public MatchSummaryResponse toSummary(JobMatch match) {
		return new MatchSummaryResponse(match.getId(), match.getJobId(), match.getScore(), match.getAiScore(), match.getJobTitle(),
				match.getJobCompany(), match.getJobLocation(), match.isJobRemote(), match.getJobPostedAt(),
				match.getJobApplyUrl(), match.getUpdatedAt());
	}

	public MatchDetailResponse toDetail(JobMatch match) {
		List<String> aiReasons = match.getAiReasons() == null ? List.of() : jsonMapper.readValue(match.getAiReasons(), REASONS);
		return new MatchDetailResponse(toSummary(match), match.getStatus(), readBreakdown(match.getBreakdown()), aiReasons,
				match.getAiScoredBy());
	}

	public String writeBreakdown(List<FactorScore> breakdown) {
		return jsonMapper.writeValueAsString(breakdown);
	}

	private List<FactorScore> readBreakdown(String json) {
		return jsonMapper.readValue(json, BREAKDOWN);
	}

}
