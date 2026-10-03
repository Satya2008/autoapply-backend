package com.naukriradar.matching.scoring;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.client.MatchingProfile;

/**
 * The candidate's "never show me" rules: companies they don't want, and words (like
 * "unpaid" or "night shift") that rule a job out. Whole-word matches only, so excluding
 * "Meta" doesn't drop "Metaflow Labs".
 */
public final class ExclusionFilter {

	private final Map<String, Pattern> companies;

	/** Lower case, matched with {@link TextMatcher#containsWord}. */
	private final Set<String> keywords;

	public ExclusionFilter(MatchingProfile profile) {
		this.companies = compile(profile.excludedCompanies());
		this.keywords = profile.excludedKeywords().stream()
				.map(k -> k.strip().toLowerCase(Locale.ROOT))
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	/** Why the job is excluded, or null if it isn't. */
	public String reason(CandidateJob job) {
		String company = job.company() == null ? "" : job.company();
		for (Map.Entry<String, Pattern> excluded : companies.entrySet()) {
			if (excluded.getValue().matcher(company).find()) {
				return "company " + excluded.getKey();
			}
		}
		String text = (job.title() == null ? "" : job.title()) + "\n" + (job.description() == null ? "" : job.description());
		String lower = text.toLowerCase(Locale.ROOT);
		for (String keyword : keywords) {
			if (TextMatcher.containsWord(lower, keyword)) {
				return "keyword " + keyword;
			}
		}
		return null;
	}

	private static Map<String, Pattern> compile(List<String> terms) {
		return terms.stream().distinct().collect(Collectors.toMap(Function.identity(), TextMatcher::wordPattern));
	}

}
