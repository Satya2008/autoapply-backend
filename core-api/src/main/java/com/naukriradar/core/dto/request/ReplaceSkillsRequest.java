package com.naukriradar.core.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The complete new skill list; skills not in it are removed. */
public record ReplaceSkillsRequest(
		@NotNull @Size(max = 100) List<@NotNull @Valid SkillRequest> skills) {
}
