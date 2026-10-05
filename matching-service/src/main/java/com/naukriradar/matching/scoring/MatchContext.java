package com.naukriradar.matching.scoring;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.naukriradar.matching.client.MatchingProfile;

/**
 * Everything about the candidate the factors need, prepared once per run instead of once per
 * job: lower-cased skills, compiled location and role patterns, role words.
 */
public final class MatchContext {

	private final MatchingProfile profile;

	private final Instant now;

	/** Lower-case skills, matched with {@link TextMatcher#containsWord}. */
	private final Set<String> skills = new LinkedHashSet<>();

	private final Map<String, Pattern> locationPatterns = new LinkedHashMap<>();

	private final List<Set<String>> roleTokens;

	private final Map<String, Pattern> rolePhrases = new LinkedHashMap<>();

	private final SemanticVectors semantic;

	private MatchContext(MatchingProfile profile, Instant now, SemanticVectors semantic) {
		this.profile = profile;
		this.now = now;
		this.semantic = semantic == null ? SemanticVectors.NONE : semantic;
		profile.skills().forEach(skill -> skills.add(skill.strip().toLowerCase(Locale.ROOT)));
		profile.preferredLocations().forEach(location -> locationPatterns.put(location, TextMatcher.wordPattern(location)));
		profile.targetRoles().forEach(role -> rolePhrases.put(role, TextMatcher.wordPattern(role)));
		this.roleTokens = profile.targetRoles().stream().map(TextMatcher::keyTokens).filter(t -> !t.isEmpty()).toList();
	}

	/** Keyword matching only. */
	public static MatchContext of(MatchingProfile profile, Instant now) {
		return new MatchContext(profile, now, SemanticVectors.NONE);
	}

	public static MatchContext of(MatchingProfile profile, Instant now, SemanticVectors semantic) {
		return new MatchContext(profile, now, semantic);
	}

	/** The same candidate, with other vectors: for scoring one profile two ways in an eval. */
	public MatchContext withSemantic(SemanticVectors semantic) {
		return new MatchContext(profile, now, semantic);
	}

	public SemanticVectors semantic() {
		return semantic;
	}

	public MatchingProfile profile() {
		return profile;
	}

	public Instant now() {
		return now;
	}

	Set<String> skills() {
		return skills;
	}

	Map<String, Pattern> locationPatterns() {
		return locationPatterns;
	}

	Map<String, Pattern> rolePhrases() {
		return rolePhrases;
	}

	List<Set<String>> roleTokens() {
		return roleTokens;
	}

}
