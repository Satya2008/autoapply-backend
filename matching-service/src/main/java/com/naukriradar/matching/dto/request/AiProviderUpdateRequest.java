package com.naukriradar.matching.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * Changes to a provider. Fields left out stay as they are. {@code apiKey} replaces the key;
 * an empty string removes it.
 */
public record AiProviderUpdateRequest(
		@Size(max = 255) @URL String baseUrl,
		@Size(max = 500) String apiKey,
		@Size(min = 1, max = 100) String model,
		@Size(max = 100) String strongModel,
		Boolean enabled,
		@Min(5) @Max(600) Integer timeoutSeconds,
		@DecimalMin("0") BigDecimal inputPrice,
		@DecimalMin("0") BigDecimal outputPrice) {
}
