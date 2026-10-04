package com.naukriradar.matching.dto.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A new prompt version. Variables in the template are written {{name}}. */
public record PromptVersionRequest(
		@Size(max = 5000) String system,
		@NotBlank @Size(max = 20000) String template,
		Map<String, Object> outputSchema) {
}
