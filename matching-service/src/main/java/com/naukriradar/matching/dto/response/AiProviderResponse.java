package com.naukriradar.matching.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

import com.naukriradar.matching.model.AiProviderType;

/**
 * A provider as the admin screen shows it. The key itself is never returned.
 *
 * @param ready enabled and has everything it needs to be called
 * @param primary the first one tried
 */
public record AiProviderResponse(String name, AiProviderType type, String baseUrl, String model, boolean enabled,
		boolean ready, boolean primary, int priority, boolean apiKeySet, String apiKeyHint, int timeoutSeconds,
		BigDecimal inputPrice, BigDecimal outputPrice, Instant updatedAt, String updatedBy) {
}
