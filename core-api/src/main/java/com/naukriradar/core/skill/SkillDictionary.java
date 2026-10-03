package com.naukriradar.core.skill;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;

/**
 * Known skills and the spellings that mean them, read from a text file:
 *
 * <pre>
 * # comment
 * spring boot | springboot
 * javascript  | js, ecmascript
 * </pre>
 *
 * The first name on a line is the canonical skill. Spaces and hyphens inside a term are
 * flexible when matching, so "spring boot" also covers "spring-boot".
 */
public class SkillDictionary {

	/** Longest names a profile can store; see the profile_skills.skill column. */
	static final int MAX_SKILL_LENGTH = 50;

	private static final Pattern COMMENT = Pattern.compile("(^|\\s)#.*$");

	/** term (lower case) to canonical skill. */
	private final Map<String, String> terms;

	private SkillDictionary(Map<String, String> terms) {
		this.terms = Collections.unmodifiableMap(terms);
	}

	public static SkillDictionary load(Resource resource) {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
			Map<String, String> terms = new LinkedHashMap<>();
			Map<String, String> separatorFree = new LinkedHashMap<>();
			String line;
			int lineNumber = 0;
			while ((line = reader.readLine()) != null) {
				lineNumber++;
				// "#" starts a comment only at line start or after a space, so "c#" survives.
				String entry = COMMENT.matcher(line).replaceFirst("").strip();
				if (entry.isEmpty()) {
					continue;
				}
				String[] parts = entry.split("\\|", 2);
				String canonical = normalise(parts[0]);
				if (canonical.isEmpty() || canonical.length() > MAX_SKILL_LENGTH) {
					throw new IllegalStateException("Bad skill on line " + lineNumber + ": '" + parts[0] + "'");
				}
				add(terms, separatorFree, canonical, canonical, lineNumber);
				if (parts.length > 1) {
					for (String alias : parts[1].split(",")) {
						String term = normalise(alias);
						if (!term.isEmpty()) {
							add(terms, separatorFree, term, canonical, lineNumber);
						}
					}
				}
			}
			return new SkillDictionary(terms);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not read skill dictionary " + resource, ex);
		}
	}

	/** Every searchable term mapped to its canonical skill. */
	public Map<String, String> terms() {
		return terms;
	}

	/** The key a matched piece of text is looked up by: lower case, no spaces or hyphens. */
	static String separatorFreeKey(String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[\\s_-]+", "");
	}

	private static void add(Map<String, String> terms, Map<String, String> separatorFree, String term,
			String canonical, int lineNumber) {
		String key = separatorFreeKey(term);
		String existing = separatorFree.putIfAbsent(key, canonical);
		if (existing != null && !existing.equals(canonical)) {
			// e.g. "react native" vs "reactnative" pointing at two different skills
			throw new IllegalStateException(
					"Line " + lineNumber + ": '" + term + "' already means '" + existing + "'");
		}
		terms.putIfAbsent(term, canonical);
	}

	private static String normalise(String value) {
		return value.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

}
