package com.naukriradar.matching.dto.response;

import java.util.List;

/**
 * @param jobsAnalysed jobs on the user's shortlist (after exclusions)
 * @param currentMatches of those, the ones at or above the threshold now
 * @param threshold the score a job needs to count as a match
 * @param gaps biggest gain first
 */
public record SkillGapResponse(int jobsAnalysed, int currentMatches, int threshold, List<Gap> gaps) {

	/**
	 * @param jobsAsking jobs that ask for this skill
	 * @param extraMatches jobs that would cross the threshold with this skill on the profile
	 * @param exampleJobs up to three of the jobs asking for it
	 */
	public record Gap(String skill, int jobsAsking, int extraMatches, List<String> exampleJobs) {
	}

}
