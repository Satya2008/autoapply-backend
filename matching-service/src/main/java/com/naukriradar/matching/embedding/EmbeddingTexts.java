package com.naukriradar.matching.embedding;

import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.client.MatchingProfile;

/**
 * What gets embedded for a profile and for a job. Both sides lead with role and skills, so the
 * vectors compare what matters; a job's description is cut, because its first part carries
 * the role and the rest is mostly about the company and benefits.
 */
public final class EmbeddingTexts {

	static final int DESCRIPTION_CHARS = 2000;

	private EmbeddingTexts() {
	}

	public static String profile(MatchingProfile profile) {
		StringBuilder text = new StringBuilder();
		line(text, "Roles", String.join(", ", profile.targetRoles()));
		line(text, "Skills", String.join(", ", profile.skills()));
		return text.toString().strip();
	}

	public static String job(CandidateJob job) {
		StringBuilder text = new StringBuilder();
		line(text, "Role", job.title());
		line(text, "Skills", String.join(", ", job.requiredSkills()));
		line(text, "Seniority", job.seniority());
		String description = job.description() == null ? "" : job.description();
		line(text, "About", description.length() > DESCRIPTION_CHARS ? description.substring(0, DESCRIPTION_CHARS) : description);
		return text.toString().strip();
	}

	private static void line(StringBuilder text, String label, String value) {
		if (value != null && !value.isBlank()) {
			text.append(label).append(": ").append(value.strip()).append('\n');
		}
	}

}
