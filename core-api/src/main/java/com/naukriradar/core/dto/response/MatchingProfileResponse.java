package com.naukriradar.core.dto.response;

import java.util.List;

/**
 * What matching-service needs from a profile, and nothing else (no contact details).
 *
 * @param expectedSalary yearly, in INR
 */
public record MatchingProfileResponse(
		String userId,
		List<String> skills,
		List<String> targetRoles,
		List<String> preferredLocations,
		boolean remoteOk,
		Long expectedSalary,
		Integer experienceYears,
		List<String> excludedCompanies,
		List<String> excludedKeywords,
		int minMatchScore) {
}
