package com.naukriradar.matching.dto.response;

import java.util.List;

import com.naukriradar.matching.model.MatchStatus;
import com.naukriradar.matching.scoring.FactorScore;

/** A match with the factor-by-factor reasons for its score, biggest contribution first. */
public record MatchDetailResponse(
		MatchSummaryResponse match,
		MatchStatus status,
		List<FactorScore> breakdown) {
}
