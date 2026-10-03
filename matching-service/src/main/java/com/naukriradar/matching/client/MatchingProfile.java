package com.naukriradar.matching.client;

import java.util.List;

/** A candidate's profile as core-api shares it with matching. Lists are never null. */
public record MatchingProfile(
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

	public MatchingProfile {
		skills = nonNull(skills);
		targetRoles = nonNull(targetRoles);
		preferredLocations = nonNull(preferredLocations);
		excludedCompanies = nonNull(excludedCompanies);
		excludedKeywords = nonNull(excludedKeywords);
	}

	private static List<String> nonNull(List<String> values) {
		return values == null ? List.of() : List.copyOf(values.stream().filter(v -> v != null && !v.isBlank()).toList());
	}

}
