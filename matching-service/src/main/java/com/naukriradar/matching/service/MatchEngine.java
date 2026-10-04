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
import com.naukriradar.matching.mapper.MatchMapper;
import com.naukriradar.matching.repository.JobMatchWriter;
import com.naukriradar.matching.repository.JobMatchWriter.ScoredJob;
import com.naukriradar.matching.scoring.ExclusionFilter;
import com.naukriradar.matching.scoring.MatchContext;
import com.naukriradar.matching.scoring.MatchScorer;
import com.naukriradar.matching.scoring.ScoreResult;
import org.springframework.stereotype.Service;

/**
 * One user's matching, start to end: profile from core-api, shortlist from job-service,
 * drop excluded jobs, score the rest, keep the ones above the threshold, and let the AI
 * review the best few. Synchronous and free
 * of threading, so it can be tested directly; {@link MatchRunWorker} runs it in the background.
 */
@Service
public class MatchEngine {

	private final CoreApiClient coreApi;
	private final JobServiceClient jobService;
	private final MatchScorer scorer;
	private final JobMatchWriter writer;
	private final MatchMapper mapper;
	private final MatchingProperties properties;
	private final AiReRanker reRanker;
	private final Clock clock = Clock.systemUTC();

	public MatchEngine(CoreApiClient coreApi, JobServiceClient jobService, MatchScorer scorer, JobMatchWriter writer,
			MatchMapper mapper, MatchingProperties properties, AiReRanker reRanker) {
		this.coreApi = coreApi;
		this.jobService = jobService;
		this.scorer = scorer;
		this.writer = writer;
		this.mapper = mapper;
		this.properties = properties;
		this.reRanker = reRanker;
	}

	public Outcome match(String userId) {
		MatchingProfile profile = coreApi.matchingProfile(userId);
		if (profile.skills().isEmpty() && profile.targetRoles().isEmpty()) {
			throw new MatchInputException("Add skills or target roles to your profile first.");
		}
		Set<String> keywords = new LinkedHashSet<>(profile.skills());
		keywords.addAll(profile.targetRoles());
		List<CandidateJob> candidates = jobService.candidates(List.copyOf(keywords), properties.candidateDays(),
				properties.candidateLimit());

		Instant now = clock.instant();
		MatchContext context = MatchContext.of(profile, now);
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
	 * @param aiReviewed how many of the top matches the AI scored
	 * @param aiNote why the AI review stopped early, or null
	 */
	public record Outcome(int jobsConsidered, int excluded, int created, int updated, int belowThreshold, int aiReviewed,
			String aiNote) {
	}

}
