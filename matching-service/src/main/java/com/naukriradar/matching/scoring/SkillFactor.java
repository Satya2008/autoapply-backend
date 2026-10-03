package com.naukriradar.matching.scoring;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import com.naukriradar.matching.client.CandidateJob;
import org.springframework.stereotype.Component;

/**
 * Share of the candidate's skills the job mentions. Measured against a handful of skills,
 * not all of them: someone listing 40 skills shouldn't score low because a posting names 5.
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

}
