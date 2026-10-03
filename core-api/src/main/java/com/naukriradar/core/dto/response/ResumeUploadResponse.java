package com.naukriradar.core.dto.response;

import java.util.List;

/**
 * @param skillsFound every skill recognised in the resume
 * @param skillsAdded the ones that were new on the profile
 * @param autoApplyTurnedOff true if the update left too few skills for auto apply, so it
 * was switched off
 */
public record ResumeUploadResponse(
		ResumeResponse resume,
		List<String> skillsFound,
		List<String> skillsAdded,
		boolean autoApplyTurnedOff) {
}
