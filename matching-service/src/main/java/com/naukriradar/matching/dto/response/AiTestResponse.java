package com.naukriradar.matching.dto.response;

import java.math.BigDecimal;

import tools.jackson.databind.JsonNode;

public record AiTestResponse(String provider, String model, JsonNode answer, long tokensIn, long tokensOut,
		BigDecimal costUsd, long latencyMs, int fallbacks) {
}
