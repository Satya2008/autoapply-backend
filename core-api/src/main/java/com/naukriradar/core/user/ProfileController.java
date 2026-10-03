package com.naukriradar.core.user;

import java.util.List;

import com.naukriradar.common.web.CurrentUserProvider;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The caller's own profile and skills. "me" means the user from {@link CurrentUserProvider}. */
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
class ProfileController {

	private final CurrentUserProvider currentUser;
	private final ProfileService profileService;

	@GetMapping("/profile")
	ProfileResponse getProfile() {
		return profileService.getProfile(currentUser.currentUserId());
	}

	@PutMapping("/profile")
	ProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
		return profileService.updateProfile(currentUser.currentUserId(), request);
	}

	@GetMapping("/skills")
	List<SkillResponse> getSkills() {
		return profileService.getSkills(currentUser.currentUserId());
	}

	@PutMapping("/skills")
	List<SkillResponse> replaceSkills(@Valid @RequestBody ReplaceSkillsRequest request) {
		return profileService.replaceSkills(currentUser.currentUserId(), request);
	}

}
