package com.naukriradar.matching.scoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.naukriradar.matching.client.CandidateJob;
import org.springframework.stereotype.Component;

/**
 * When the posting has been parsed, the share of the skills it requires that the candidate
 * has. Otherwise the share of the candidate's skills the posting mentions, measured against a
 * handful of skills: someone listing 40 skills shouldn't score low because a posting names 5.
 */
@Component
public class SkillFactor implements ScoringFactor {

	/** A posting naming this many of your skills counts as a full skill match. */
	static final int FULL_MATCH_SKILLS = 6;

	@Override
	public String name() {
		return "skills";
	}

	@Override
	public FactorResult score(MatchContext context, CandidateJob job) {
		Set<String> skills = context.skills();
		if (skills.isEmpty()) {
			return new FactorResult(0, "No skills on your profile.");
		}
		if (!job.requiredSkills().isEmpty()) {
			return againstRequirements(skills, job.requiredSkills());
		}
		String lower = ((job.title() == null ? "" : job.title()) + "\n" + (job.description() == null ? "" : job.description()))
				.toLowerCase(Locale.ROOT);
		List<String> found = new ArrayList<>();
		for (String skill : skills) {
			if (TextMatcher.containsWord(lower, skill)) {
				found.add(skill);
			}
		}
		if (found.isEmpty()) {
			return new FactorResult(0, "None of your skills are mentioned.");
		}
		double needed = Math.min(skills.size(), FULL_MATCH_SKILLS);
		return new FactorResult(found.size() / needed,
				"Mentions " + found.size() + " of your skills: " + String.join(", ", found.subList(0, Math.min(found.size(), 8))) + ".");
	}

	private static FactorResult againstRequirements(Set<String> skills, List<String> required) {
		List<String> have = new ArrayList<>();
		List<String> missing = new ArrayList<>();
		for (String skill : required) {
			String wanted = skill.strip().toLowerCase(Locale.ROOT);
			// "spring boot" required and "spring" on the profile (or the other way) both count
			boolean match = skills.stream().anyMatch(s -> s.equals(wanted) || TextMatcher.containsWord(wanted, s)
					|| TextMatcher.containsWord(s, wanted));
			(match ? have : missing).add(skill);
		}
		double needed = Math.min(required.size(), FULL_MATCH_SKILLS);
		String reason = "Has " + have.size() + " of the " + required.size() + " skills the job asks for"
				+ (missing.isEmpty() ? "." : "; missing " + String.join(", ", missing.subList(0, Math.min(missing.size(), 5))) + ".");
		return new FactorResult(Math.min(1, have.size() / needed), reason);
	}

}
