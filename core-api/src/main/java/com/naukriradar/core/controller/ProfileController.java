package com.naukriradar.core.controller;

import java.util.List;

import com.naukriradar.common.security.CurrentUserProvider;
import com.naukriradar.core.dto.request.ReplaceSkillsRequest;
import com.naukriradar.core.dto.request.UpdateProfileRequest;
import com.naukriradar.core.dto.response.ProfileResponse;
import com.naukriradar.core.dto.response.SkillResponse;
import com.naukriradar.core.service.ProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class ProfileController {

	private final CurrentUserProvider currentUser;
	private final ProfileService profileService;

	@GetMapping("/profile")
	public ProfileResponse getProfile() {
		return profileService.getProfile(currentUser.currentUserId());
	}

	@PutMapping("/profile")
	public ProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
		return profileService.updateProfile(currentUser.currentUserId(), request);
	}

	@GetMapping("/skills")
	public List<SkillResponse> getSkills() {
		return profileService.getSkills(currentUser.currentUserId());
	}

	@PutMapping("/skills")
	public List<SkillResponse> replaceSkills(@Valid @RequestBody ReplaceSkillsRequest request) {
		return profileService.replaceSkills(currentUser.currentUserId(), request);
	}

}
