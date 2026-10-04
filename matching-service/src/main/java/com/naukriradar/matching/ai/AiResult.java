package com.naukriradar.matching.ai;

import tools.jackson.databind.JsonNode;

/**
 * A usable answer.
 *
 * @param json the checked answer when the request had a schema, else null
 * @param fallbacks how many providers failed before this one answered
 */
public record AiResult(String provider, String model, String text, JsonNode json, long tokensIn, long tokensOut,
		long costMicros, long latencyMs, int fallbacks) {
}
