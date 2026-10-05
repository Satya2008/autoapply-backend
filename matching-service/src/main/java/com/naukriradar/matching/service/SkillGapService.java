package com.naukriradar.matching.service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.naukriradar.common.exception.BusinessRuleException;
import com.naukriradar.common.exception.ServiceUnavailableException;
import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.client.CoreApiClient;
import com.naukriradar.matching.client.JobServiceClient;
import com.naukriradar.matching.client.MatchingProfile;
import com.naukriradar.matching.client.UpstreamException;
import com.naukriradar.matching.config.MatchingProperties;
import com.naukriradar.matching.dto.response.SkillGapResponse;
import com.naukriradar.matching.embedding.HashingEmbedder;
import com.naukriradar.matching.scoring.ExclusionFilter;
import com.naukriradar.matching.scoring.MatchContext;
import com.naukriradar.matching.scoring.MatchScorer;
import org.springframework.stereotype.Service;

/**
 * "Learn Docker and you match 14 more jobs." Looks at the jobs on the user's shortlist, counts
 * the skills they ask for that the profile lacks, and for the most asked ones re-scores every
 * job as if the user had that skill: the jobs that cross the match threshold are the gain.
 *
 * <p>Re-scoring, not just counting, because a skill that many jobs mention may still not move
 * any of them over the line, and that is what the user cares about. Keyword scoring only
 * (semantic factor left out): it is the part a new skill changes, and it costs no API call.
 */
@Service
public class SkillGapService {

	/** Skills in the report. */
	static final int REPORT = 10;

	/** The most asked skills that get re-scored; bounds the work to CONSIDER x jobs scores. */
	static final int CONSIDER = 25;

	private static final Set<String> KEYWORD_ONLY = Set.of("semantic");

	private final CoreApiClient coreApi;
	private final JobServiceClient jobService;
	private final MatchScorer scorer;
	private final MatchingProperties properties;
	private final Set<String> vocabulary;
	private final Clock clock = Clock.systemUTC();

	public SkillGapService(CoreApiClient coreApi, JobServiceClient jobService, MatchScorer scorer,
			MatchingProperties properties, HashingEmbedder embedder) {
		this.coreApi = coreApi;
		this.jobService = jobService;
		this.scorer = scorer;
		this.properties = properties;
		this.vocabulary = embedder.skillVocabulary();
	}

	public SkillGapResponse report(String userId) {
		try {
			return build(userId);
		}
		catch (UpstreamException ex) {
			throw new ServiceUnavailableException(ex.getMessage());
		}
	}

	private SkillGapResponse build(String userId) {
		MatchingProfile profile = coreApi.matchingProfile(userId);
		if (profile.skills().isEmpty() && profile.targetRoles().isEmpty()) {
			throw new BusinessRuleException("Add skills or target roles to your profile first.");
		}
		Set<String> keywords = new LinkedHashSet<>(profile.targetRoles());
		keywords.addAll(profile.skills());
		ExclusionFilter exclusions = new ExclusionFilter(profile);
		List<CandidateJob> jobs = jobService.candidates(List.copyOf(keywords), properties.candidateDays(),
				properties.candidateLimit()).stream()
				.filter(job -> job.id() != null && exclusions.reason(job) == null)
				.toList();
		int threshold = Math.max(properties.minStoreScore(), profile.minMatchScore());
		Instant now = clock.instant();

		Map<String, Integer> baseline = scores(profile, jobs, now);
		int currentMatches = (int) baseline.values().stream().filter(score -> score >= threshold).count();

		Set<String> owned = new LinkedHashSet<>();
		profile.skills().forEach(skill -> owned.add(skill.strip().toLowerCase(Locale.ROOT)));
		Map<String, Integer> demand = new HashMap<>();
		Map<String, List<String>> examples = new HashMap<>();
		for (CandidateJob job : jobs) {
			for (String skill : asked(job)) {
				if (has(owned, skill)) {
					continue;
				}
				demand.merge(skill, 1, Integer::sum);
				List<String> titles = examples.computeIfAbsent(skill, s -> new ArrayList<>());
				if (titles.size() < 3) {
					titles.add(job.title() + (job.company() == null ? "" : " at " + job.company()));
				}
			}
		}

		List<SkillGapResponse.Gap> gaps = new ArrayList<>();
		demand.entrySet().stream()
				.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
				.limit(CONSIDER)
				.forEach(entry -> {
					Map<String, Integer> with = scores(withSkill(profile, entry.getKey()), jobs, now);
					int extra = (int) with.entrySet().stream()
							.filter(e -> e.getValue() >= threshold && baseline.get(e.getKey()) < threshold)
							.count();
					gaps.add(new SkillGapResponse.Gap(entry.getKey(), entry.getValue(), extra, examples.get(entry.getKey())));
				});
		gaps.sort(Comparator.comparingInt(SkillGapResponse.Gap::extraMatches).reversed()
				.thenComparing(Comparator.comparingInt(SkillGapResponse.Gap::jobsAsking).reversed())
				.thenComparing(SkillGapResponse.Gap::skill));
		return new SkillGapResponse(jobs.size(), currentMatches, threshold,
				List.copyOf(gaps.subList(0, Math.min(REPORT, gaps.size()))));
	}

	private Map<String, Integer> scores(MatchingProfile profile, List<CandidateJob> jobs, Instant now) {
		MatchContext context = MatchContext.of(profile, now);
		Map<String, Integer> scores = new LinkedHashMap<>();
		for (CandidateJob job : jobs) {
			scores.putIfAbsent(job.id(), scorer.score(context, job, KEYWORD_ONLY).total());
		}
		return scores;
	}

	/**
	 * The skills a job asks for: the AI-parsed list when there is one, else the known skills
	 * its title and description mention.
	 */
	private Set<String> asked(CandidateJob job) {
		Set<String> skills = new LinkedHashSet<>();
		if (!job.requiredSkills().isEmpty()) {
			job.requiredSkills().forEach(skill -> {
				String lower = skill.strip().toLowerCase(Locale.ROOT);
				if (!lower.isEmpty() && lower.length() <= 40) {
					skills.add(lower);
				}
			});
			return skills;
		}
		String text = ((job.title() == null ? "" : job.title()) + "\n" + (job.description() == null ? "" : job.description()))
				.toLowerCase(Locale.ROOT);
		for (String term : vocabulary) {
			if (HashingEmbedder.containsWord(text, term)) {
				skills.add(term);
			}
		}
		return skills;
	}

	/** Same rule as the skills factor: "spring" on the profile covers "spring boot" asked, and the other way. */
	private static boolean has(Set<String> owned, String skill) {
		return owned.stream().anyMatch(s -> s.equals(skill) || HashingEmbedder.containsWord(skill, s)
				|| HashingEmbedder.containsWord(s, skill));
	}

	private static MatchingProfile withSkill(MatchingProfile p, String skill) {
		List<String> skills = new ArrayList<>(p.skills());
		skills.add(skill);
		return new MatchingProfile(p.userId(), skills, p.targetRoles(), p.preferredLocations(), p.remoteOk(),
				p.expectedSalary(), p.experienceYears(), p.excludedCompanies(), p.excludedKeywords(), p.minMatchScore());
	}

}
