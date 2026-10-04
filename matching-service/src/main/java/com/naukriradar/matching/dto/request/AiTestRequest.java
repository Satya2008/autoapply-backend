package com.naukriradar.matching.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Sends the ai-test prompt. With a provider (and optionally a model) only that one is tried;
 * without, the prompt's normal route is used.
 */
public record AiTestRequest(@Size(max = 50) String provider, @Size(max = 100) String model,
		@Size(max = 200) String topic) {
}
