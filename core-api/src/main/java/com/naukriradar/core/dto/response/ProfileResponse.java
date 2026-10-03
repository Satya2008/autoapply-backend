package com.naukriradar.core.dto.response;

import java.time.Instant;
import java.util.List;

public record ProfileResponse(
		String userId,
		String fullName,
		String phone,
		String location,
		String currentTitle,
		Integer experienceYears,
		Long expectedSalary,
		Integer noticePeriodDays,
		String linkedinUrl,
		String githubUrl,
		String portfolioUrl,
		List<String> targetRoles,
		List<String> preferredLocations,
		List<String> excludedCompanies,
		List<String> excludedKeywords,
		boolean remoteOk,
		int minMatchScore,
		int dailyApplyLimit,
		boolean autoApplyEnabled,
		Instant updatedAt) {
}
