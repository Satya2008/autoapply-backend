package com.naukriradar.matching.dto.request;

import java.math.BigDecimal;

import com.naukriradar.matching.model.AiProviderType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * A new AI provider account.
 *
 * @param baseUrl leave out for the type's usual address; set it for OpenAI-compatible services
 * @param strongModel a better model for writing tasks (cover letters); optional
 * @param enabled defaults to true
 * @param inputPrice USD per million input tokens, for the cost report; optional
 */
public record AiProviderCreateRequest(
		@NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{0,49}", message = "lowercase letters, digits and dashes") String name,
		@NotNull AiProviderType type,
		@Size(max = 255) @URL String baseUrl,
		@Size(max = 500) String apiKey,
		@NotBlank @Size(max = 100) String model,
		@Size(max = 100) String strongModel,
		Boolean enabled,
		@Min(5) @Max(600) Integer timeoutSeconds,
		@DecimalMin("0") BigDecimal inputPrice,
		@DecimalMin("0") BigDecimal outputPrice) {
}
