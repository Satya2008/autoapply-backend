package com.naukriradar.core.controller;

import com.naukriradar.core.dto.response.MatchingProfileResponse;
import com.naukriradar.core.service.ProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API. The gateway has no route to /internal, so only other services on
 * the private network can call it.
 */
@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
public class InternalProfileController {

	private final ProfileService profileService;

	@GetMapping("/{userId}/matching-profile")
	public MatchingProfileResponse matchingProfile(@PathVariable String userId) {
		return profileService.getMatchingProfile(userId);
	}

}
