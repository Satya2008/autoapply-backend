package com.naukriradar.user;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ProfileResponse(
		UUID userId,
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
