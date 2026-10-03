package com.naukriradar.core.dto.response;

import com.naukriradar.core.model.SkillSource;

public record SkillResponse(
		String name,
		Integer years,
		SkillSource source) {
}
