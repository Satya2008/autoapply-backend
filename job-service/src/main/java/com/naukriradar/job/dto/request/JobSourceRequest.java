package com.naukriradar.job.dto.request;

import java.util.Map;

import com.naukriradar.job.model.RequestMethod;
import com.naukriradar.job.model.SourceType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Create or fully replace a job source. Deeper checks (JsonPaths compile, placeholders are
 * known, required fields are mapped) happen in {@code JobSourceValidator}.
 */
public record JobSourceRequest(
		@NotBlank @Pattern(regexp = "[a-z0-9][a-z0-9-]{1,39}", message = "must be 2-40 lower-case letters, digits or dashes")
		String code,
		@NotBlank @Size(max = 100) String name,
		@NotNull SourceType type,
		@NotBlank @Size(max = 500) String baseUrl,
		@Size(max = 500) String searchPath,
		@NotNull RequestMethod method,
		@Size(max = 10_000) String bodyTemplate,
		@Size(max = 20) Map<@NotBlank @Size(max = 100) String, @NotNull @Size(max = 1000) String> headers,
		@Size(max = 30) Map<@NotBlank @Size(max = 100) String, @NotNull @Size(max = 500) String> queryParams,
		@NotBlank @Size(max = 300) String resultsPath,
		@NotEmpty Map<@NotBlank String, @NotBlank @Size(max = 300) String> fieldMappings,
		boolean enabled,
		@Min(0) @Max(1000) int priority,
		@Min(1) @Max(60) int timeoutSeconds,
		@Min(1) @Max(20) int maxPages) {
}
