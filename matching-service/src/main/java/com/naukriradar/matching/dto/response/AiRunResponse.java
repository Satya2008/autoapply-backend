package com.naukriradar.matching.dto.response;

import java.math.BigDecimal;

import tools.jackson.databind.JsonNode;

/**
 * @param answer the checked JSON answer; null when AI couldn't answer
 * @param unavailableReason why there is no answer (AI off, budget spent, every provider
 *     failed); a normal outcome, not an error, so callers don't retry it
 */
public record AiRunResponse(String provider, String model, JsonNode answer, BigDecimal costUsd, String unavailableReason) {

	public static AiRunResponse unavailable(String reason) {
		return new AiRunResponse(null, null, null, BigDecimal.ZERO, reason);
	}

}
