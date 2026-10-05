package com.naukriradar.matching.controller;

import com.naukriradar.common.security.CurrentUserProvider;
import com.naukriradar.matching.dto.response.SkillGapResponse;
import com.naukriradar.matching.service.SkillGapService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/skill-gap")
public class SkillGapController {

	private final CurrentUserProvider currentUser;
	private final SkillGapService skillGap;

	public SkillGapController(CurrentUserProvider currentUser, SkillGapService skillGap) {
		this.currentUser = currentUser;
		this.skillGap = skillGap;
	}

	/** The skills missing from your profile that would bring you the most extra matches. */
	@GetMapping
	public SkillGapResponse report() {
		return skillGap.report(currentUser.currentUserId());
	}

}
