package com.naukriradar.matching.dto.response;

import java.util.List;

import com.naukriradar.matching.model.AiProviderType;

/**
 * What the "add provider" form needs to know about each type.
 *
 * @param supportsEmbeddings whether it can be picked for semantic matching
 */
public record AiProviderTypeResponse(AiProviderType type, String label, String defaultBaseUrl, boolean needsApiKey,
		List<String> exampleModels, boolean supportsEmbeddings, List<String> exampleEmbeddingModels, String note) {
}
