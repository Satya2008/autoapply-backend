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
		int minMatchScore,
		Filters filters) {

	public MatchingProfile {
		skills = nonNull(skills);
		targetRoles = nonNull(targetRoles);
		preferredLocations = nonNull(preferredLocations);
		excludedCompanies = nonNull(excludedCompanies);
		excludedKeywords = nonNull(excludedKeywords);
		filters = filters == null ? Filters.NONE : filters;
	}

	/** Without job filters: every job may be matched. */
	public MatchingProfile(String userId, List<String> skills, List<String> targetRoles, List<String> preferredLocations,
			boolean remoteOk, Long expectedSalary, Integer experienceYears, List<String> excludedCompanies,
			List<String> excludedKeywords, int minMatchScore) {
		this(userId, skills, targetRoles, preferredLocations, remoteOk, expectedSalary, experienceYears, excludedCompanies,
				excludedKeywords, minMatchScore, null);
	}

	/**
	 * Hard rules: a job that breaks one isn't matched at all.
	 *
	 * @param mustHave the job must be about at least one of these; empty for no rule
	 * @param region INDIA (jobs in India, remote jobs open to India) or ANYWHERE
	 * @param level JUNIOR_MID, UP_TO_SENIOR or ANY
	 */
	public record Filters(List<String> mustHave, String region, String level) {

		public static final Filters NONE = new Filters(List.of(), "ANYWHERE", "ANY");

		public Filters {
			mustHave = nonNull(mustHave);
			region = region == null || region.isBlank() ? "ANYWHERE" : region;
			level = level == null || level.isBlank() ? "ANY" : level;
		}

	}

	private static List<String> nonNull(List<String> values) {
		return values == null ? List.of() : List.copyOf(values.stream().filter(v -> v != null && !v.isBlank()).toList());
	}

}
