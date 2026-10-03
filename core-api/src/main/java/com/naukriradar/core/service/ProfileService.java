package com.naukriradar.core.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.naukriradar.common.exception.BusinessRuleException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.dto.request.ReplaceSkillsRequest;
import com.naukriradar.core.dto.request.SkillRequest;
import com.naukriradar.core.dto.request.UpdateProfileRequest;
import com.naukriradar.core.dto.response.ProfileResponse;
import com.naukriradar.core.dto.response.SkillResponse;
import com.naukriradar.core.mapper.ProfileMapper;
import com.naukriradar.core.model.Profile;
import com.naukriradar.core.model.ProfileSkill;
import com.naukriradar.core.model.SkillSource;
import com.naukriradar.core.repository.ProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProfileService {

	static final int MIN_SKILLS_FOR_AUTO_APPLY = 3;

	/** Same cap as the PUT /skills request, so resume parsing can't go past it either. */
	static final int MAX_SKILLS = 100;

	private final ProfileRepository profileRepository;
	private final ProfileMapper mapper;

	@Transactional(readOnly = true)
	public ProfileResponse getProfile(String userId) {
		return mapper.toResponse(load(userId));
	}

	@Transactional
	public ProfileResponse updateProfile(String userId, UpdateProfileRequest request) {
		Profile profile = load(userId);
		mapper.copyScalars(request, profile);
		replace(profile.getTargetRoles(), normaliseLabels(request.targetRoles()).values());
		replace(profile.getPreferredLocations(), normaliseLabels(request.preferredLocations()).values());
		replace(profile.getExcludedCompanies(), normaliseLabels(request.excludedCompanies()).keySet());
		replace(profile.getExcludedKeywords(), normaliseLabels(request.excludedKeywords()).keySet());
		checkAutoApplyPrerequisites(profile);
		// flush so the response has the new updatedAt and version
		return mapper.toResponse(profileRepository.saveAndFlush(profile));
	}

	@Transactional(readOnly = true)
	public List<SkillResponse> getSkills(String userId) {
		return mapper.toResponse(load(userId).getSkills());
	}

	@Transactional
	public List<SkillResponse> replaceSkills(String userId, ReplaceSkillsRequest request) {
		Profile profile = load(userId);
		Map<String, ProfileSkill> skills = new HashMap<>();
		for (SkillRequest skill : request.skills()) {
			skills.merge(normalise(skill.name()).toLowerCase(Locale.ROOT),
					new ProfileSkill(skill.years(), SkillSource.MANUAL), ProfileService::moreExperienced);
		}
		profile.getSkills().clear();
		profile.getSkills().putAll(skills);
		checkAutoApplyPrerequisites(profile);
		return mapper.toResponse(profileRepository.saveAndFlush(profile).getSkills());
	}

	/**
	 * Swaps the resume-sourced skills for the ones just found. Skills the user typed in are
	 * never touched. If the swap leaves too few skills for auto apply, auto apply is turned
	 * off rather than failing the upload.
	 */
	@Transactional
	public ResumeSkillsUpdate applyResumeSkills(String userId, Collection<String> found) {
		Profile profile = load(userId);
		Map<String, ProfileSkill> skills = profile.getSkills();
		skills.entrySet().removeIf(e -> e.getValue().getSource() == SkillSource.RESUME && !found.contains(e.getKey()));

		List<String> added = new ArrayList<>();
		for (String name : found) {
			if (skills.size() >= MAX_SKILLS) {
				break;
			}
			if (!skills.containsKey(name)) {
				skills.put(name, new ProfileSkill(null, SkillSource.RESUME));
				added.add(name);
			}
		}

		boolean autoApplyTurnedOff = false;
		if (profile.isAutoApplyEnabled() && !meetsAutoApplyPrerequisites(profile)) {
			profile.setAutoApplyEnabled(false);
			autoApplyTurnedOff = true;
		}
		profileRepository.saveAndFlush(profile);
		return new ResumeSkillsUpdate(added, autoApplyTurnedOff);
	}

	public record ResumeSkillsUpdate(List<String> added, boolean autoApplyTurnedOff) {
	}

	private Profile load(String userId) {
		return profileRepository.findById(userId)
				.orElseThrow(() -> new NotFoundException("No profile for user " + userId + "."));
	}

	/** Auto apply acts on the candidate's behalf, so it needs enough to match on. */
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

	private static boolean meetsAutoApplyPrerequisites(Profile profile) {
		return !profile.getTargetRoles().isEmpty() && profile.getSkills().size() >= MIN_SKILLS_FOR_AUTO_APPLY;
	}

	/**
	 * Trims, collapses whitespace, drops blanks and de-duplicates ignoring case. Keys are
	 * lower case; values keep the first spelling the user sent.
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
		return value == null ? "" : value.strip().replaceAll("\\s+", " ");
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

	/** Changes the managed collection in place instead of swapping the instance Hibernate tracks. */
	private static void replace(Collection<String> target, Collection<String> values) {
		target.clear();
		target.addAll(values);
	}

}
