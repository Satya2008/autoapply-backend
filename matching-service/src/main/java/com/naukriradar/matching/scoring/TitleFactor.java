package com.naukriradar.matching.scoring;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.naukriradar.matching.client.CandidateJob;
import org.springframework.stereotype.Component;

/**
 * How close the job title is to a role the candidate wants. A title containing the role
 * ("Senior Backend Engineer" for "Backend Engineer") is a full match; otherwise it is the
 * share of the role's words found in the title. Seniority words are ignored on both sides,
 * since experience is scored separately.
 */
@Component
public class TitleFactor implements ScoringFactor {

	@Override
	public String name() {
		return "title";
	}

	@Override
	public FactorResult score(MatchContext context, CandidateJob job) {
		if (context.rolePhrases().isEmpty()) {
			return FactorResult.unknown("No target roles on your profile.");
		}
		String title = job.title() == null ? "" : job.title();
		for (Map.Entry<String, Pattern> role : context.rolePhrases().entrySet()) {
			if (role.getValue().matcher(title).find()) {
				return new FactorResult(1, "Title matches your target role \"" + role.getKey() + "\".");
			}
		}
		Set<String> titleTokens = TextMatcher.keyTokens(title);
		double best = 0;
		for (Set<String> role : context.roleTokens()) {
			long shared = role.stream().filter(titleTokens::contains).count();
			best = Math.max(best, (double) shared / role.size());
		}
		if (best == 0) {
			return new FactorResult(0, "Title doesn't match your target roles.");
		}
		return new FactorResult(best, "Title shares " + Math.round(best * 100) + "% of the words of your closest target role.");
	}

}
