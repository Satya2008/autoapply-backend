package com.naukriradar.matching.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.naukriradar.matching.dto.request.RetrieveRequest;
import com.naukriradar.matching.dto.response.RetrieveResponse;
import com.naukriradar.matching.embedding.Bm25;
import com.naukriradar.matching.embedding.EmbeddingService;
import com.naukriradar.matching.embedding.Embeddings;
import com.naukriradar.matching.embedding.Vectors;
import org.springframework.stereotype.Service;

/**
 * The "R" of RAG: given a question and some passages (a resume's chunks), the few passages
 * that answer it best, so a prompt carries three relevant paragraphs instead of a whole
 * resume: a sharper answer, fewer tokens, and less room to make things up.
 *
 * <p>Hybrid: meaning (embedding cosine) finds "built REST services" for "API experience";
 * BM25 keeps exact names ("Kafka", "Infosys") from being drowned out.
 */
@Service
public class RetrievalService {

	/** Share of the score from meaning; the rest is keywords. */
	static final double SEMANTIC_SHARE = 0.7;

	private final EmbeddingService embeddings;

	public RetrievalService(EmbeddingService embeddings) {
		this.embeddings = embeddings;
	}

	public RetrieveResponse rank(RetrieveRequest request) {
		List<RetrieveRequest.Passage> passages = request.passages();
		int top = request.topOrDefault();
		if (passages.isEmpty()) {
			return new RetrieveResponse(null, List.of());
		}
		List<String> texts = new ArrayList<>(passages.size() + 1);
		texts.add(request.query());
		passages.forEach(p -> texts.add(p.text()));
		// one call, so the question and the passages come from the same model
		Embeddings embedded = embeddings.embed(texts);
		float[] query = embedded.vectors().getFirst();
		List<Double> lexical = Bm25.scores(request.query(), passages.stream().map(RetrieveRequest.Passage::text).toList());
		double maxLexical = lexical.stream().mapToDouble(Double::doubleValue).max().orElse(0);

		List<RetrieveResponse.Ranked> ranked = new ArrayList<>(passages.size());
		for (int i = 0; i < passages.size(); i++) {
			double semantic = Math.max(0, Vectors.cosine(query, embedded.vectors().get(i + 1)));
			double keyword = maxLexical == 0 ? 0 : lexical.get(i) / maxLexical;
			double score = SEMANTIC_SHARE * semantic + (1 - SEMANTIC_SHARE) * keyword;
			ranked.add(new RetrieveResponse.Ranked(passages.get(i).id(), round(score), round(semantic), round(keyword)));
		}
		ranked.sort(Comparator.comparingDouble(RetrieveResponse.Ranked::score).reversed());
		return new RetrieveResponse(embedded.modelKey(), List.copyOf(ranked.subList(0, Math.min(top, ranked.size()))));
	}

	private static double round(double value) {
		return Math.round(value * 1000) / 1000.0;
	}

}
