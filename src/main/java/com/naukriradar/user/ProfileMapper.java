package com.naukriradar.user;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Converts between entities and API records. Entities never leave the service layer;
 * everything a client sees goes through here.
 */
@Component
class ProfileMapper {

	UserResponse toResponse(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getStatus(), user.getCreatedAt());
	}

	ProfileResponse toResponse(Profile profile) {
		return new ProfileResponse(
				profile.getUserId(),
				profile.getFullName(),
				profile.getPhone(),
				profile.getLocation(),
				profile.getCurrentTitle(),
				profile.getExperienceYears(),
				profile.getExpectedSalary(),
				profile.getNoticePeriodDays(),
				profile.getLinkedinUrl(),
				profile.getGithubUrl(),
				profile.getPortfolioUrl(),
				sorted(profile.getTargetRoles()),
				sorted(profile.getPreferredLocations()),
				sorted(profile.getExcludedCompanies()),
				sorted(profile.getExcludedKeywords()),
				profile.isRemoteOk(),
				profile.getMinMatchScore(),
				profile.getDailyApplyLimit(),
				profile.isAutoApplyEnabled(),
				profile.getUpdatedAt());
	}

	List<SkillResponse> toResponse(Map<String, ProfileSkill> skills) {
		return skills.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(e -> new SkillResponse(e.getKey(), e.getValue().getYears(), e.getValue().getSource()))
				.toList();
	}

	/** Copies the scalar fields. Collections are normalised and set by the service. */
	void copyScalars(UpdateProfileRequest request, Profile profile) {
		profile.setFullName(request.fullName());
		profile.setPhone(request.phone());
		profile.setLocation(request.location());
		profile.setCurrentTitle(request.currentTitle());
		profile.setExperienceYears(request.experienceYears());
		profile.setExpectedSalary(request.expectedSalary());
		profile.setNoticePeriodDays(request.noticePeriodDays());
		profile.setLinkedinUrl(request.linkedinUrl());
		profile.setGithubUrl(request.githubUrl());
		profile.setPortfolioUrl(request.portfolioUrl());
		profile.setRemoteOk(request.remoteOk());
		profile.setMinMatchScore(request.minMatchScore());
		profile.setDailyApplyLimit(request.dailyApplyLimit());
		profile.setAutoApplyEnabled(request.autoApplyEnabled());
	}

	private static List<String> sorted(Collection<String> values) {
		return values.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
	}

}
