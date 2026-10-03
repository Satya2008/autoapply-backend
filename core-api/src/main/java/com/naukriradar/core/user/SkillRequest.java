package com.naukriradar.core.user;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillRequest(
		@NotBlank @Size(max = 50) String name,
		@Min(0) @Max(60) Integer years) {
}
