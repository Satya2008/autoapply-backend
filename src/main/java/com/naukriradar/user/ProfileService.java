package com.naukriradar.user;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.naukriradar.common.BusinessRuleException;
import com.naukriradar.common.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** All profile rules live here: normalisation, limits and the auto-apply prerequisites. */
@Service
@RequiredArgsConstructor
public class ProfileService {

	static final int MIN_SKILLS_FOR_AUTO_APPLY = 3;

	private final ProfileRepository profiles;
	private final ProfileMapper mapper;

	@Transactional(readOnly = true)
	public ProfileResponse getProfile(UUID userId) {
		return mapper.toResponse(load(userId));
	}

	@Transactional
	public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
		Profile profile = load(userId);
		mapper.copyScalars(request, profile);
		replace(profile.getTargetRoles(), normaliseLabels(request.targetRoles()).values());
		replace(profile.getPreferredLocations(), normaliseLabels(request.preferredLocations()).values());
		replace(profile.getExcludedCompanies(), normaliseLabels(request.excludedCompanies()).keySet());
		replace(profile.getExcludedKeywords(), normaliseLabels(request.excludedKeywords()).keySet());
		checkAutoApplyPrerequisites(profile);
		// Flush so the response carries the new updatedAt and version.
		return mapper.toResponse(profiles.saveAndFlush(profile));
	}

	@Transactional(readOnly = true)
	public List<SkillResponse> getSkills(UUID userId) {
		return mapper.toResponse(load(userId).getSkills());
	}

	@Transactional
	public List<SkillResponse> replaceSkills(UUID userId, ReplaceSkillsRequest request) {
		Profile profile = load(userId);
		Map<String, ProfileSkill> skills = new HashMap<>();
		for (SkillRequest skill : request.skills()) {
			skills.merge(normalise(skill.name()).toLowerCase(Locale.ROOT),
					new ProfileSkill(skill.years(), SkillSource.MANUAL), ProfileService::moreExperienced);
		}
		profile.getSkills().clear();
		profile.getSkills().putAll(skills);
		checkAutoApplyPrerequisites(profile);
		return mapper.toResponse(profiles.saveAndFlush(profile).getSkills());
	}

	private Profile load(UUID userId) {
		return profiles.findById(userId)
				.orElseThrow(() -> new NotFoundException("No profile for user " + userId + "."));
	}

	/** Auto apply acts on the candidate's behalf, so it needs enough data to match on. */
	private static void checkAutoApplyPrerequisites(Profile profile) {
		if (!profile.isAutoApplyEnabled()) {
			return;
		}
		if (profile.getTargetRoles().isEmpty()) {
			throw new BusinessRuleException("Add at least one target role before turning on auto apply.");
		}
		if (profile.getSkills().size() < MIN_SKILLS_FOR_AUTO_APPLY) {
			throw new BusinessRuleException("Add at least " + MIN_SKILLS_FOR_AUTO_APPLY
					+ " skills before turning on auto apply.");
		}
	}

	/**
	 * Trims and collapses whitespace, drops blanks, and de-duplicates ignoring case.
	 * Keys are lower case; values keep the first spelling the user sent.
	 */
	private static Map<String, String> normaliseLabels(Collection<String> raw) {
		Map<String, String> result = new LinkedHashMap<>();
		if (raw == null) {
			return result;
		}
		for (String value : raw) {
			String clean = normalise(value);
			if (!clean.isEmpty()) {
				result.putIfAbsent(clean.toLowerCase(Locale.ROOT), clean);
			}
		}
		return result;
	}

	private static String normalise(String value) {
		return value == null ? "" : value.trim().replaceAll("\\s+", " ");
	}

	private static ProfileSkill moreExperienced(ProfileSkill a, ProfileSkill b) {
		if (a.getYears() == null) {
			return b;
		}
		if (b.getYears() == null) {
			return a;
		}
		return a.getYears() >= b.getYears() ? a : b;
	}

	/** Mutates the managed collection in place rather than swapping the instance Hibernate tracks. */
	private static void replace(Collection<String> target, Collection<String> values) {
		target.clear();
		target.addAll(values);
	}

}
