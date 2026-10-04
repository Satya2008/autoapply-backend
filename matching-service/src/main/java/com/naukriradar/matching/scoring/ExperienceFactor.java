package com.naukriradar.matching.scoring;

import com.naukriradar.matching.client.CandidateJob;
import org.springframework.stereotype.Component;

/**
 * Candidate's years against what the posting asks. Falling short hurts fast (recruiters
 * filter on it); being a little over hardly matters.
 */
@Component
public class ExperienceFactor implements ScoringFactor {

	@Override
	public String name() {
		return "experience";
	}

	@Override
	public FactorResult score(MatchContext context, CandidateJob job) {
		Integer years = context.profile().experienceYears();
		if (years == null) {
			return FactorResult.unknown("No experience on your profile.");
		}
		// the AI-parsed minimum is more reliable than reading numbers out of the text
		ExperienceParser.Range wanted = job.minYearsExperience() != null
				? new ExperienceParser.Range(job.minYearsExperience(), null)
				: ExperienceParser.parse(job.title(), job.description());
		if (wanted == null) {
			return FactorResult.unknown("The posting doesn't say how much experience it wants.");
		}
		String asks = "Asks " + wanted.describe() + ", you have " + years + ".";
		if (years < wanted.min()) {
			int gap = wanted.min() - years;
			double score = gap == 1 ? 0.6 : gap == 2 ? 0.3 : 0.1;
			return new FactorResult(score, asks);
		}
		if (wanted.max() != null && years > wanted.max()) {
			return new FactorResult(years - wanted.max() <= 2 ? 0.8 : 0.5, asks);
		}
		return new FactorResult(1, asks);
	}

}
