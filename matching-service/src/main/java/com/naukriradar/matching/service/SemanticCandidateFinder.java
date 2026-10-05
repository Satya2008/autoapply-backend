package com.naukriradar.matching.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.client.JobServiceClient;
import com.naukriradar.matching.client.MatchingProfile;
import com.naukriradar.matching.client.UpstreamException;
import com.naukriradar.matching.config.SemanticProperties;
import com.naukriradar.matching.embedding.EmbeddingService;
import com.naukriradar.matching.embedding.EmbeddingTexts;
import com.naukriradar.matching.embedding.Embeddings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * The semantic half of the shortlist: jobs whose meaning is close to the profile, found in the
 * vector index, which keyword search can miss ("Spring Microservices Engineer" for someone
 * looking for "Java Backend"). {@link #fuse} merges both lists by reciprocal rank.
 *
 * <p>A bonus, never a dependency: when anything here fails, the run goes on with keyword
 * candidates only.
 */
@Service
public class SemanticCandidateFinder {

	private static final Logger log = LoggerFactory.getLogger(SemanticCandidateFinder.class);

	/** The usual RRF constant: damps the gap between rank 1 and rank 2 so neither list dominates. */
	static final int RRF_K = 60;

	private final EmbeddingService embeddings;
	private final JobVectorIndex index;
	private final JobServiceClient jobService;
	private final SemanticProperties semantic;

	public SemanticCandidateFinder(EmbeddingService embeddings, JobVectorIndex index, JobServiceClient jobService,
			SemanticProperties semantic) {
		this.embeddings = embeddings;
		this.index = index;
		this.jobService = jobService;
		this.semantic = semantic;
	}

	public Result find(MatchingProfile profile) {
		if (!semantic.enabled()) {
			return Result.OFF;
		}
		Embeddings embedded = embeddings.embed(List.of(EmbeddingTexts.profile(profile)));
		float[] vector = embedded.vectors().getFirst();
		if (vector.length == 0 || semantic.candidateTop() == 0) {
			return new Result(embedded.modelKey(), vector, List.of());
		}
		List<JobVectorIndex.Hit> hits = index.search(vector, embedded.modelKey(), semantic.candidateTop());
		if (hits.isEmpty()) {
			return new Result(embedded.modelKey(), vector, List.of());
		}
		try {
			List<CandidateJob> jobs = jobService.byIds(hits.stream().map(JobVectorIndex.Hit::jobId).toList());
			return new Result(embedded.modelKey(), vector, jobs);
		}
		catch (UpstreamException ex) {
			log.warn("Semantic candidates skipped for {}: {}", profile.userId(), ex.getMessage());
			return new Result(embedded.modelKey(), vector, List.of());
		}
	}

	/**
	 * Reciprocal rank fusion: each list votes 1 / (60 + rank) for its jobs, and the votes add
	 * up. It needs no score calibration between the two lists (FULLTEXT relevance and cosine
	 * aren't on the same scale), and a job both lists like rises to the top.
	 */
	public static List<CandidateJob> fuse(List<CandidateJob> keyword, List<CandidateJob> semantic, int limit) {
		Map<String, Double> votes = new HashMap<>();
		Map<String, CandidateJob> jobs = new LinkedHashMap<>();
		vote(keyword, votes, jobs);
		vote(semantic, votes, jobs);
		List<CandidateJob> fused = new ArrayList<>(jobs.values());
		fused.sort((a, b) -> Double.compare(votes.get(b.id()), votes.get(a.id())));
		return fused.size() > limit ? List.copyOf(fused.subList(0, limit)) : fused;
	}

	private static void vote(List<CandidateJob> ranked, Map<String, Double> votes, Map<String, CandidateJob> jobs) {
		Set<String> seen = new HashSet<>();
		int rank = 0;
		for (CandidateJob job : ranked) {
			// a job listed twice in one list votes once, at its best rank
			if (job.id() == null || !seen.add(job.id())) {
				continue;
			}
			rank++;
			jobs.putIfAbsent(job.id(), job);
			votes.merge(job.id(), 1.0 / (RRF_K + rank), Double::sum);
		}
	}

	/**
	 * @param modelKey the model the profile vector came from; null when semantic matching is off
	 * @param profileVector empty when off
	 * @param jobs nearest jobs first, still active
	 */
	public record Result(String modelKey, float[] profileVector, List<CandidateJob> jobs) {

		static final Result OFF = new Result(null, new float[0], List.of());

		public boolean on() {
			return modelKey != null && profileVector.length > 0;
		}

	}

}
