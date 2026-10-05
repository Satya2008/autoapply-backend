package com.naukriradar.matching.embedding;

import java.util.List;

/**
 * Vectors for some texts, all from one model.
 *
 * @param modelKey which model made them; only vectors with the same key may be compared
 * @param vectors unit length, one per text, in the order asked
 * @param fellBack the chosen provider failed and the local embedder stood in
 */
public record Embeddings(String modelKey, List<float[]> vectors, boolean fellBack) {
}
