package com.naukriradar.matching.embedding;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The built-in embedder: no network, no key, no cost, so semantic matching works from the
 * first start. It hashes words, word pairs and concept groups into a fixed-size vector
 * ("feature hashing"). The concept groups ({@code embeddings/concepts.json}) are what make
 * "Spring Microservices Engineer" land near "Java Backend Developer" although they share no
 * word.
 *
 * <p>It is lexical with a little domain knowledge, not a language model: a hosted embedding
 * model understands much more. Pick one on any provider (embedding model field) and it takes
 * over; the matcher eval shows the difference in numbers.
 */
@Component
public class HashingEmbedder {

	public static final String MODEL_KEY = "local:hashing-v1";

	static final int DIMENSIONS = 512;

	private static final double CONCEPT_WEIGHT = 2.0;

	private static final double PAIR_WEIGHT = 0.5;

	private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}#+]+(?:\\.[\\p{L}\\p{N}]+)*");

	private static final Set<String> STOPWORDS = Set.of("a", "an", "and", "are", "as", "at", "be", "by", "for", "from",
			"has", "have", "in", "is", "it", "of", "on", "or", "our", "that", "the", "their", "this", "to", "we", "will",
			"with", "you", "your", "who", "can", "all", "any", "not", "but", "into", "about", "us", "they", "them", "its",
			"was", "were", "been", "being", "also", "more", "such", "other", "etc", "using", "use", "work", "working",
			"team", "teams", "experience", "years", "year", "job", "role", "skills", "ability", "strong", "good",
			"knowledge", "looking", "candidate", "required", "requirements", "plus", "must", "should", "would");

	/** concept name -> its phrases, lower case */
	private final Map<String, List<String>> concepts;

	@Autowired
	public HashingEmbedder(JsonMapper json) {
		this(loadConcepts(json));
	}

	HashingEmbedder(Map<String, List<String>> concepts) {
		this.concepts = Map.copyOf(concepts);
	}

	/** Unit length; an empty or wordless text gives the zero vector (similar to nothing). */
	public float[] embed(String text) {
		float[] vector = new float[DIMENSIONS];
		if (text == null || text.isBlank()) {
			return vector;
		}
		String lower = text.toLowerCase(Locale.ROOT);
		List<String> words = words(lower);
		Map<String, Integer> counts = new HashMap<>();
		words.forEach(word -> counts.merge("w:" + word, 1, Integer::sum));
		for (int i = 1; i < words.size(); i++) {
			counts.merge("p:" + words.get(i - 1) + " " + words.get(i), 1, Integer::sum);
		}
		// a word said ten times isn't ten times as important: 1 + ln(count)
		counts.forEach((feature, count) -> add(vector, feature,
				(feature.startsWith("p:") ? PAIR_WEIGHT : 1.0) * (1 + Math.log(count))));
		concepts.forEach((concept, phrases) -> {
			if (phrases.stream().anyMatch(phrase -> containsWord(lower, phrase))) {
				add(vector, "c:" + concept, CONCEPT_WEIGHT);
			}
		});
		return Vectors.normalize(vector);
	}

	/** Lower-case content words, in order: no stop words, and no single letters except the languages C and R. */
	public static List<String> words(String text) {
		List<String> words = new ArrayList<>();
		if (text == null) {
			return words;
		}
		Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
		while (matcher.find()) {
			String word = matcher.group();
			if (!STOPWORDS.contains(word) && (word.length() > 1 || word.equals("c") || word.equals("r"))) {
				words.add(word);
			}
		}
		return words;
	}

	/**
	 * Signed feature hashing: two independent hashes pick the slot and the sign, so features
	 * that collide on a slot tend to cancel out rather than pile up.
	 */
	private static void add(float[] vector, String feature, double weight) {
		int slot = Math.floorMod(mix(feature.hashCode()), DIMENSIONS);
		int sign = (mix(feature.hashCode() ^ 0x5bd1e995) & 1) == 0 ? 1 : -1;
		vector[slot] += (float) (sign * weight);
	}

	/** The murmur3 finalizer: spreads String.hashCode's weak low bits over the whole int. */
	private static int mix(int h) {
		h ^= h >>> 16;
		h *= 0x85ebca6b;
		h ^= h >>> 13;
		h *= 0xc2b2ae35;
		h ^= h >>> 16;
		return h;
	}

	/** Whole-word match where + # . belong to the word, so "java" isn't found in "javascript". */
	public static boolean containsWord(String lowerText, String lowerTerm) {
		int at = lowerText.indexOf(lowerTerm);
		while (at >= 0) {
			int end = at + lowerTerm.length();
			boolean startOk = at == 0 || !isWordChar(lowerText.charAt(at - 1));
			boolean endOk = end == lowerText.length() || !isWordChar(lowerText.charAt(end))
					|| (lowerText.charAt(end) == '.' && (end + 1 == lowerText.length() || !Character.isLetterOrDigit(lowerText.charAt(end + 1))));
			if (startOk && endOk) {
				return true;
			}
			at = lowerText.indexOf(lowerTerm, at + 1);
		}
		return false;
	}

	private static boolean isWordChar(char c) {
		return Character.isLetterOrDigit(c) || c == '+' || c == '#' || c == '.';
	}

	private static Map<String, List<String>> loadConcepts(JsonMapper json) {
		try (InputStream in = new ClassPathResource("embeddings/concepts.json").getInputStream()) {
			JsonNode root = json.readTree(in);
			Map<String, List<String>> concepts = new LinkedHashMap<>();
			root.path("concepts").properties().forEach(entry -> {
				List<String> phrases = new ArrayList<>();
				entry.getValue().forEach(phrase -> phrases.add(phrase.asString().strip().toLowerCase(Locale.ROOT)));
				concepts.put(entry.getKey(), List.copyOf(phrases));
			});
			if (concepts.isEmpty()) {
				throw new IllegalStateException("embeddings/concepts.json has no concepts");
			}
			return concepts;
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Can't read embeddings/concepts.json", ex);
		}
	}

	/**
	 * The concept phrases that name a learnable skill (a tool, language or practice), for the
	 * skill-gap report: role words and broad areas like "backend" or "cloud" are left out.
	 */
	public Set<String> skillVocabulary() {
		return concepts.entrySet().stream()
				.filter(e -> !NOT_SKILLS_CONCEPTS.contains(e.getKey()))
				.flatMap(e -> e.getValue().stream())
				.filter(phrase -> !NOT_SKILLS_PHRASES.contains(phrase))
				.collect(Collectors.toUnmodifiableSet());
	}

	private static final Set<String> NOT_SKILLS_CONCEPTS = Set.of("software-engineering", "management");

	private static final Set<String> NOT_SKILLS_PHRASES = Set.of("backend", "back-end", "back end", "server-side",
			"server side", "api", "apis", "frontend", "front-end", "front end", "full stack", "full-stack", "fullstack",
			"node", "go", "cloud", "mobile", "testing", "tester", "database", "databases", "security", "embedded",
			"data engineer", "data scientist", "ai engineer", "platform engineer", "ui developer", "ux", "jvm",
			"engineer", "developer", "data engineering", "data science", "software development", "dba", "qa",
			"ui designer", "product designer", "ux designer", "manual testing");

}
