package com.naukriradar.matching.embedding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BM25, the classic keyword relevance score, over a small set of passages: rewards passages
 * that contain the query's rarer words, and doesn't let a long passage win just by being long.
 * The lexical half of hybrid retrieval; exact names (a framework, a company) are where
 * embeddings are weakest and keywords strongest.
 */
public final class Bm25 {

	static final double K1 = 1.2;

	static final double B = 0.75;

	private Bm25() {
	}

	/** One score per passage, in order; 0 for a passage sharing no word with the query. */
	public static List<Double> scores(String query, List<String> passages) {
		List<List<String>> docs = passages.stream().map(HashingEmbedder::words).toList();
		Set<String> terms = new LinkedHashSet<>(HashingEmbedder.words(query));
		List<Double> scores = new ArrayList<>(docs.size());
		if (docs.isEmpty() || terms.isEmpty()) {
			docs.forEach(d -> scores.add(0.0));
			return scores;
		}
		double averageLength = docs.stream().mapToInt(List::size).average().orElse(0);
		Map<String, Integer> documentFrequency = new HashMap<>();
		for (List<String> doc : docs) {
			for (String term : new HashSet<>(doc)) {
				if (terms.contains(term)) {
					documentFrequency.merge(term, 1, Integer::sum);
				}
			}
		}
		int n = docs.size();
		for (List<String> doc : docs) {
			Map<String, Integer> frequency = new HashMap<>();
			doc.forEach(word -> frequency.merge(word, 1, Integer::sum));
			double score = 0;
			for (String term : terms) {
				int tf = frequency.getOrDefault(term, 0);
				if (tf == 0) {
					continue;
				}
				int df = documentFrequency.getOrDefault(term, 0);
				double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
				double lengthNorm = averageLength == 0 ? 1 : doc.size() / averageLength;
				score += idf * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * lengthNorm));
			}
			scores.add(score);
		}
		return scores;
	}

}
