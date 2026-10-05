package com.naukriradar.matching.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.client.CoreApiClient;
import com.naukriradar.matching.client.JobServiceClient;
import com.naukriradar.matching.client.MatchingProfile;
import com.naukriradar.matching.config.MatchingProperties;
import com.naukriradar.matching.config.SemanticProperties;
import com.naukriradar.matching.mapper.MatchMapper;
import com.naukriradar.matching.repository.JobMatchWriter;
import com.naukriradar.matching.repository.JobMatchWriter.ScoredJob;
import com.naukriradar.matching.scoring.ExclusionFilter;
import com.naukriradar.matching.scoring.MatchContext;
import com.naukriradar.matching.scoring.MatchScorer;
import com.naukriradar.matching.scoring.ScoreResult;
import com.naukriradar.matching.scoring.SemanticVectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * One user's matching, start to end: profile from core-api, a hybrid shortlist (keyword
 * search in job-service plus the nearest jobs by meaning), drop excluded jobs, score the
 * rest, keep the ones above the threshold, and let the AI review the best few. Synchronous and free
 * of threading, so it can be tested directly; {@link MatchRunWorker} runs it in the background.
 */
@Service
public class MatchEngine {

	private static final Logger log = LoggerFactory.getLogger(MatchEngine.class);

	private final CoreApiClient coreApi;
	private final JobServiceClient jobService;
	private final MatchScorer scorer;
	private final JobMatchWriter writer;
	private final MatchMapper mapper;
	private final MatchingProperties properties;
	private final AiReRanker reRanker;
	private final SemanticCandidateFinder semanticFinder;
	private final JobEmbeddingIndexer indexer;
	private final SemanticProperties semanticProperties;
	private final Clock clock = Clock.systemUTC();

	public MatchEngine(CoreApiClient coreApi, JobServiceClient jobService, MatchScorer scorer, JobMatchWriter writer,
			MatchMapper mapper, MatchingProperties properties, AiReRanker reRanker, SemanticCandidateFinder semanticFinder,
			JobEmbeddingIndexer indexer, SemanticProperties semanticProperties) {
		this.coreApi = coreApi;
		this.jobService = jobService;
		this.scorer = scorer;
		this.writer = writer;
		this.mapper = mapper;
		this.properties = properties;
		this.reRanker = reRanker;
		this.semanticFinder = semanticFinder;
		this.indexer = indexer;
		this.semanticProperties = semanticProperties;
	}

	public Outcome match(String userId) {
		MatchingProfile profile = coreApi.matchingProfile(userId);
		if (profile.skills().isEmpty() && profile.targetRoles().isEmpty()) {
			throw new MatchInputException("Add skills or target roles to your profile first.");
		}
		Set<String> keywords = new LinkedHashSet<>(profile.skills());
		keywords.addAll(profile.targetRoles());
		List<CandidateJob> keywordCandidates = jobService.candidates(List.copyOf(keywords), properties.candidateDays(),
				properties.candidateLimit());
		Shortlist shortlist = semanticShortlist(profile, keywordCandidates);
		List<CandidateJob> candidates = shortlist.jobs();

		Instant now = clock.instant();
		MatchContext context = MatchContext.of(profile, now, shortlist.vectors());
		ExclusionFilter exclusions = new ExclusionFilter(profile);
		List<ScoredJob> keep = new ArrayList<>();
		List<String> drop = new ArrayList<>();
		int excluded = 0;
		Set<String> seen = new LinkedHashSet<>();
		for (CandidateJob job : candidates) {
			if (job.id() == null || !seen.add(job.id())) {
				continue;
			}
			if (exclusions.reason(job) != null) {
				excluded++;
				// a newly excluded company should vanish from earlier results too
				drop.add(job.id());
				continue;
			}
			ScoreResult result = scorer.score(context, job);
			if (result.total() >= properties.minStoreScore()) {
				keep.add(new ScoredJob(job, result.total(), mapper.writeBreakdown(result.breakdown())));
			}
			else {
				drop.add(job.id());
			}
		}

		JobMatchWriter.Counts counts = writer.upsert(userId, keep, now);
		writer.deleteFor(userId, drop);
		AiReRanker.Outcome ai = reRanker.rerank(profile, keep);
		return new Outcome(seen.size(), excluded, counts.created(), counts.updated(), drop.size() - excluded, ai.scored(),
				ai.note());
	}

	/**
	 * Keyword shortlist plus the jobs nearest in meaning, fused by rank, with every job's
	 * vector. Semantic matching is a bonus: if any part of it fails, the keyword shortlist is
	 * scored on its own, exactly as before.
	 */
	private Shortlist semanticShortlist(MatchingProfile profile, List<CandidateJob> keyword) {
		try {
			SemanticCandidateFinder.Result semantic = semanticFinder.find(profile);
			if (!semantic.on()) {
				return new Shortlist(keyword, SemanticVectors.NONE);
			}
			List<CandidateJob> fused = SemanticCandidateFinder.fuse(keyword, semantic.jobs(),
					properties.candidateLimit() + semanticProperties.candidateTop());
			JobEmbeddingIndexer.Ensured ensured = indexer.ensure(fused, semantic.modelKey());
			log.debug("Shortlist for {}: {} by keyword, {} by meaning, {} together", profile.userId(), keyword.size(),
					semantic.jobs().size(), fused.size());
			return new Shortlist(fused, new SemanticVectors(semantic.modelKey(), semantic.profileVector(), ensured.vectors(),
					semanticProperties.range(semantic.modelKey())));
		}
		catch (RuntimeException ex) {
			log.warn("Semantic matching skipped for {}: {}", profile.userId(), ex.toString());
			return new Shortlist(keyword, SemanticVectors.NONE);
		}
	}

	private record Shortlist(List<CandidateJob> jobs, SemanticVectors vectors) {
	}

	/**
	 * @param aiReviewed how many of the top matches the AI scored
	 * @param aiNote why the AI review stopped early, or null
	 */
	public record Outcome(int jobsConsidered, int excluded, int created, int updated, int belowThreshold, int aiReviewed,
			String aiNote) {
	}

}
