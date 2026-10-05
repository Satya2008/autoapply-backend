package com.naukriradar.matching.dto.response;

import java.time.Instant;

import com.naukriradar.matching.model.EvalKind;
import com.naukriradar.matching.model.EvalRunStatus;
import tools.jackson.databind.JsonNode;

/**
 * @param passed null until finished
 * @param metrics the numbers and per-case results once finished
 * @param error why it failed
 */
public record EvalRunResponse(String id, EvalKind kind, String promptCode, Integer promptVersion, EvalRunStatus status,
		Boolean passed, int caseCount, JsonNode metrics, String error, Instant createdAt, Instant finishedAt) {
}
