package com.naukriradar.matching.scoring;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Whole-word, case-insensitive matching that also works for c#, c++, .net and node.js. */
final class TextMatcher {

	/** Words that say how senior a role is, not what it is. */
	static final Set<String> SENIORITY = Set.of("senior", "sr", "junior", "jr", "lead", "principal", "staff",
			"head", "chief", "intern", "trainee", "fresher", "associate", "mid", "level", "ii", "iii", "iv");

	private static final Set<String> FILLER = Set.of("and", "or", "the", "of", "for", "in", "at", "to", "a", "an",
			"with", "m", "w", "f", "d", "x", "h", "all", "genders", "gender");

	private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}#+]+(?:\\.[\\p{L}\\p{N}]+)*");

	private TextMatcher() {
	}

	/** Matches the term only as a whole word: "java" not in "javascript", "c" not in "c++". */
	static Pattern wordPattern(String term) {
		return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(term.strip()) + "(?![\\p{L}\\p{N}+#])",
				Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	/**
	 * Same rule as {@link #wordPattern} without a regex: both arguments must already be lower
	 * case. Scoring calls this thousands of times per run on long descriptions, where a plain
	 * indexOf loop is several times faster than a case-insensitive regex.
	 */
	static boolean containsWord(String lowerText, String lowerTerm) {
		if (lowerTerm.isEmpty()) {
			return false;
		}
		int at = lowerText.indexOf(lowerTerm);
		while (at >= 0) {
			int end = at + lowerTerm.length();
			boolean startOk = at == 0 || !Character.isLetterOrDigit(lowerText.charAt(at - 1));
			boolean endOk = end == lowerText.length() || !isWordChar(lowerText.charAt(end));
			if (startOk && endOk) {
				return true;
			}
			at = lowerText.indexOf(lowerTerm, at + 1);
		}
		return false;
	}

	private static boolean isWordChar(char c) {
		return Character.isLetterOrDigit(c) || c == '+' || c == '#';
	}

	/** Meaningful lower-case words: no seniority, no filler like "(m/w/d)". */
	static Set<String> keyTokens(String text) {
		if (text == null) {
			return Set.of();
		}
		return TOKEN.matcher(text.toLowerCase(Locale.ROOT)).results()
				.map(m -> m.group())
				.filter(t -> !SENIORITY.contains(t) && !FILLER.contains(t))
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

}
