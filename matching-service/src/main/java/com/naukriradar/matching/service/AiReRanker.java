package com.naukriradar.matching.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.naukriradar.matching.ai.AiResult;
import com.naukriradar.matching.ai.AiRouter;
import com.naukriradar.matching.ai.AiUnavailableException;
import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.client.MatchingProfile;
import com.naukriradar.matching.config.MatchingProperties;
import com.naukriradar.matching.repository.JobMatchWriter;
import com.naukriradar.matching.repository.JobMatchWriter.ScoredJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Asks the AI about the best few local matches only. Scoring every job with AI would cost
 * users x jobs calls; the local scorer already ranks all of them for free, so the AI only
 * sharpens the top of the list, where the user actually looks.
 *
 * <p>AI is a bonus here, never a dependency: when it is off, out of budget or down, the run
 * keeps its local scores and says so.
 */
@Component
public class AiReRanker {

	private static final Logger log = LoggerFactory.getLogger(AiReRanker.class);

	static final String PROMPT = "job-fit";

	private static final int DESCRIPTION_CHARS = 3000;

	private final AiRouter router;
	private final JobMatchWriter writer;
	private final MatchingProperties properties;
	private final JsonMapper json;
	private final Clock clock = Clock.systemUTC();

	public AiReRanker(AiRouter router, JobMatchWriter writer, MatchingProperties properties, JsonMapper json) {
		this.router = router;
		this.writer = writer;
		this.properties = properties;
		this.json = json;
	}

	public Outcome rerank(MatchingProfile profile, List<ScoredJob> matches) {
		if (properties.aiRerankTop() <= 0 || matches.isEmpty()) {
			return new Outcome(0, null);
		}
		List<ScoredJob> top = matches.stream()
				.sorted(Comparator.comparingInt(ScoredJob::score).reversed())
				.limit(properties.aiRerankTop())
				.toList();
		String candidate = describe(profile);
		int scored = 0;
		for (ScoredJob match : top) {
			AiResult result;
			try {
				result = router.run(PROMPT, Map.of("profile", candidate, "job", describe(match.job())), profile.userId());
			}
			catch (AiUnavailableException ex) {
				log.info("AI re-rank stopped after {} of {} for {}: {}", scored, top.size(), profile.userId(), ex.getMessage());
				return new Outcome(scored, "AI review skipped: " + ex.getMessage());
			}
			JsonNode answer = result.json();
			List<String> reasons = new ArrayList<>();
			answer.path("reasons").forEach(reason -> reasons.add(reason.asString()));
			writer.saveAiScore(profile.userId(), match.job().id(), answer.path("score").asInt(),
					json.writeValueAsString(reasons), result.provider() + ":" + result.model(), clock.instant());
			scored++;
		}
		return new Outcome(scored, null);
	}

	static String describe(MatchingProfile profile) {
		StringBuilder text = new StringBuilder();
		line(text, "Skills", String.join(", ", profile.skills()));
		line(text, "Wants roles", String.join(", ", profile.targetRoles()));
		line(text, "Years of experience", profile.experienceYears() == null ? null : profile.experienceYears().toString());
		line(text, "Preferred locations", String.join(", ", profile.preferredLocations()));
		line(text, "Open to remote", profile.remoteOk() ? "yes" : "no");
		return text.toString().strip();
	}

	static String describe(CandidateJob job) {
		StringBuilder text = new StringBuilder();
		line(text, "Title", job.title());
		line(text, "Company", job.company());
		line(text, "Location", job.location() + (job.remote() ? " (remote)" : ""));
		line(text, "Required skills", String.join(", ", job.requiredSkills()));
		line(text, "Minimum years", job.minYearsExperience() == null ? null : job.minYearsExperience().toString());
		line(text, "Seniority", job.seniority());
		String description = job.description() == null ? "" : job.description();
		line(text, "Description", description.length() > DESCRIPTION_CHARS ? description.substring(0, DESCRIPTION_CHARS) : description);
		return text.toString().strip();
	}

	private static void line(StringBuilder text, String label, String value) {
		if (value != null && !value.isBlank()) {
			text.append(label).append(": ").append(value).append('\n');
		}
	}

	/** @param note why the AI review stopped early, or null */
	public record Outcome(int scored, String note) {
	}

}
