package com.naukriradar.matching.ai;

import java.util.List;

/**
 * What an embeddings API sent back.
 *
 * @param vectors one per text, in the order asked
 * @param tokens input tokens billed, 0 when the vendor doesn't say
 */
public record AiEmbeddings(List<float[]> vectors, long tokens) {
}
