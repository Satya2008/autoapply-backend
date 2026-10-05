package com.naukriradar.matching.dto.response;

import java.util.Map;

/**
 * @param currentModel the model new vectors come from
 * @param local whether that is the built-in embedder (no provider has an embedding model)
 * @param jobsWithVectors stored job vectors of the current model
 * @param storedByModel stored job vectors per model, old models included
 * @param indexModel the model this instance's in-memory index holds, or null before the first search
 * @param indexSize jobs in this instance's in-memory index
 */
public record EmbeddingStatusResponse(String currentModel, boolean local, long jobsWithVectors,
		Map<String, Long> storedByModel, String indexModel, int indexSize) {
}
