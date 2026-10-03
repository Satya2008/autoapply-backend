package com.naukriradar.core.mapper;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.naukriradar.core.dto.request.UpdateProfileRequest;
import com.naukriradar.core.dto.response.ProfileResponse;
import com.naukriradar.core.dto.response.SkillResponse;
import com.naukriradar.core.dto.response.UserResponse;
import com.naukriradar.core.model.Profile;
import com.naukriradar.core.model.ProfileSkill;
import com.naukriradar.core.model.User;
import org.springframework.stereotype.Component;

@Component
public class ProfileMapper {

	public UserResponse toResponse(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getStatus(), user.getCreatedAt());
	}

	public ProfileResponse toResponse(Profile profile) {
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

	public List<SkillResponse> toResponse(Map<String, ProfileSkill> skills) {
		return skills.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(e -> new SkillResponse(e.getKey(), e.getValue().getYears(), e.getValue().getSource()))
				.toList();
	}

	/** Copies the scalar fields; blank text becomes null. Collections are handled by the service. */
	public void copyScalars(UpdateProfileRequest request, Profile profile) {
		profile.setFullName(trimToNull(request.fullName()));
		profile.setPhone(trimToNull(request.phone()));
		profile.setLocation(trimToNull(request.location()));
		profile.setCurrentTitle(trimToNull(request.currentTitle()));
		profile.setExperienceYears(request.experienceYears());
		profile.setExpectedSalary(request.expectedSalary());
		profile.setNoticePeriodDays(request.noticePeriodDays());
		profile.setLinkedinUrl(trimToNull(request.linkedinUrl()));
		profile.setGithubUrl(trimToNull(request.githubUrl()));
		profile.setPortfolioUrl(trimToNull(request.portfolioUrl()));
		profile.setRemoteOk(request.remoteOk());
		profile.setMinMatchScore(request.minMatchScore());
		profile.setDailyApplyLimit(request.dailyApplyLimit());
		profile.setAutoApplyEnabled(request.autoApplyEnabled());
	}

	private static String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.strip();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private static List<String> sorted(Collection<String> values) {
		return values.stream().sorted(String.CASE_INSENSITIVE_ORDER).toList();
	}

}
