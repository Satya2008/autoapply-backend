package com.naukriradar.core.skill;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

/**
 * Finds known skills in free text.
 *
 * <p>All terms go into one regex, longest first, wrapped in word boundaries that also
 * treat {@code + # .} as part of a word. So "java" does not fire inside "javascript",
 * "c" never fires inside "c++", and "node.js" is taken whole before "js" gets a chance.
 */
@Component
public class SkillExtractor {

	/** Characters that may not touch a match on the left: letters, digits, underscore. */
	private static final String LEFT_BOUNDARY = "(?<![\\p{L}\\p{N}_])";

	/** On the right we also reject + and #, so "c" can't match the start of "c++" or "c#". */
	private static final String RIGHT_BOUNDARY = "(?![\\p{L}\\p{N}_+#])";

	private final Pattern pattern;

	/** separator-free matched text to canonical skill */
	private final Map<String, String> canonicalByKey = new HashMap<>();

	public SkillExtractor(SkillDictionary dictionary) {
		dictionary.terms().forEach((term, canonical) ->
				canonicalByKey.put(SkillDictionary.separatorFreeKey(term), canonical));
		List<String> longestFirst = dictionary.terms().keySet().stream()
				.sorted(Comparator.comparingInt(String::length).reversed().thenComparing(Comparator.naturalOrder()))
				.toList();
		String alternatives = longestFirst.stream()
				.map(SkillExtractor::toRegex)
				.collect(Collectors.joining("|"));
		this.pattern = Pattern.compile(LEFT_BOUNDARY + "(?:" + alternatives + ")" + RIGHT_BOUNDARY);
	}

	/** Canonical skills in the order they first appear in the text, without duplicates. */
	public Set<String> extract(String text) {
		Set<String> found = new LinkedHashSet<>();
		if (text == null || text.isBlank()) {
			return found;
		}
		Matcher matcher = pattern.matcher(text.toLowerCase(Locale.ROOT));
		while (matcher.find()) {
			String canonical = canonicalByKey.get(SkillDictionary.separatorFreeKey(matcher.group()));
			if (canonical != null) {
				found.add(canonical);
			}
		}
		return found;
	}

	/** "spring boot" becomes {@code spring[\s_-]*boot}, matching "spring boot", "spring-boot" and "springboot". */
	private static String toRegex(String term) {
		return Arrays.stream(term.split("[\\s-]+"))
				.map(Pattern::quote)
				.collect(Collectors.joining("[\\s_-]*"));
	}

}
