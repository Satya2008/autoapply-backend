package com.naukriradar.matching.dto.response;

import java.util.List;

/**
 * @param modelKey the embedding model used
 * @param passages best first
 */
public record RetrieveResponse(String modelKey, List<Ranked> passages) {

	/**
	 * @param score 0-1, what they are ranked by
	 * @param semantic cosine of the meaning, 0-1
	 * @param keyword BM25, relative to the best passage, 0-1
	 */
	public record Ranked(String id, double score, double semantic, double keyword) {
	}

}
