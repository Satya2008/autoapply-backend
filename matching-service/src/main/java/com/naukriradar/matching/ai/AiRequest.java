package com.naukriradar.matching.ai;

import java.util.Map;

/**
 * One question for a model.
 *
 * @param purpose what it's for (a prompt code); picks the route and labels the cost
 * @param userId whose budget it counts against; null for system and admin calls
 * @param outputSchema JSON Schema of the expected answer, or null for free text
 */
public record AiRequest(String purpose, String userId, String system, String prompt, Map<String, Object> outputSchema,
		int maxTokens) {
}
