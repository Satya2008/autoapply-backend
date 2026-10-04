package com.naukriradar.matching.dto.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Another service asking for an AI answer.
 *
 * @param userId whose budget it counts against; null for system work like job parsing
 * @param fresh skip the cache ("write it again")
 */
public record AiRunRequest(
		@NotBlank @Size(max = 50) String prompt,
		Map<String, String> variables,
		@Size(max = 36) String userId,
		Boolean fresh) {

	public boolean freshAnswer() {
		return Boolean.TRUE.equals(fresh);
	}

}
