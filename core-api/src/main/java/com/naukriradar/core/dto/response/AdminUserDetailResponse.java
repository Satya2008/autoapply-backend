package com.naukriradar.core.dto.response;

import java.util.List;

/** One user, read-only, for the admin: their profile, skills, resume and applications. */
public record AdminUserDetailResponse(AdminUserResponse user, ProfileResponse profile, List<SkillResponse> skills,
		String resumeFileName, ApplicationStatsResponse applicationStats, List<ApplicationSummaryResponse> recentApplications,
		int matches) {
}
