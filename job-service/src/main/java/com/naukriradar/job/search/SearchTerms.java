package com.naukriradar.job.search;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Turns what a user typed into a safe MySQL FULLTEXT boolean query plus, for words the
 * FULLTEXT index can't see, a list of title patterns.
 *
 * <p>InnoDB FULLTEXT ignores words shorter than 3 characters and anything with symbols, so
 * "go", "c#", "c++" or ".net" would match nothing. Those become word-boundary REGEXP checks on
 * the title instead. Characters that mean something in boolean mode ({@code + - < > ( ) ~ * " @})
 * never reach MySQL from user input.
 */
public final class SearchTerms {

	/** InnoDB default innodb_ft_min_token_size. */
	static final int MIN_FULLTEXT_TOKEN = 3;

	static final int MAX_TERMS = 10;

	private static final Pattern WHITESPACE = Pattern.compile("\\s+");

	/** Leading junk except . (for .net), trailing junk except # and + (for c#, c++). */
	private static final Pattern EDGE_PUNCTUATION = Pattern.compile("^[^\\p{L}\\p{N}.]+|[^\\p{L}\\p{N}#+]+$");

	private static final Pattern ALNUM_TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");

	private static final Pattern PLAIN_WORD = Pattern.compile("[\\p{L}\\p{N}]{" + MIN_FULLTEXT_TOKEN + ",84}");

	/** InnoDB's default stopwords that people actually type; they would make a "+" term match nothing. */
	private static final Set<String> STOPWORDS = Set.of("the", "and", "for", "with", "about", "are", "from",
			"how", "that", "this", "was", "what", "when", "where", "who", "will", "www");

	private final String fullText;

	private final List<String> titlePatterns;

	private SearchTerms(String fullText, List<String> titlePatterns) {
		this.fullText = fullText;
		this.titlePatterns = titlePatterns;
	}

	public static SearchTerms parse(String query) {
		if (query == null || query.isBlank()) {
			return new SearchTerms(null, List.of());
		}
		Set<String> words = new LinkedHashSet<>();
		Set<String> patterns = new LinkedHashSet<>();
		for (String token : WHITESPACE.split(query.strip().toLowerCase(Locale.ROOT))) {
			// "java," and "(spring)" are just java and spring
			String raw = EDGE_PUNCTUATION.matcher(token).replaceAll("");
			if (words.size() + patterns.size() >= MAX_TERMS) {
				break;
			}
			if (PLAIN_WORD.matcher(raw).matches()) {
				if (!STOPWORDS.contains(raw)) {
					words.add(raw);
				}
			}
			else if (ALNUM_TOKEN.matcher(raw).find()) {
				// short or symbol-carrying term: c#, c++, go, .net, node.js
				patterns.add(wordPattern(raw));
			}
		}
		String fullText = words.isEmpty() ? null : words.stream().map(w -> "+" + w).collect(Collectors.joining(" "));
		return new SearchTerms(fullText, new ArrayList<>(patterns));
	}

	/** Boolean-mode query with every word required, or null if there are no plain words. */
	public String fullText() {
		return fullText;
	}

	/** MySQL (ICU) regular expressions to run against the title. */
	public List<String> titlePatterns() {
		return titlePatterns;
	}

	/**
	 * Matches the term as a whole word. Plain \b doesn't work next to symbols ("c++" ends in a
	 * non-word character), so the boundary is written out: not preceded or followed by a letter
	 * or digit.
	 */
	private static String wordPattern(String term) {
		StringBuilder escaped = new StringBuilder();
		for (char c : term.toCharArray()) {
			if ("\\^$.|?*+()[]{}".indexOf(c) >= 0) {
				escaped.append('\\');
			}
			escaped.append(c);
		}
		return "(^|[^[:alnum:]])" + escaped + "([^[:alnum:]]|$)";
	}

}
