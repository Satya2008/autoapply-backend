package com.naukriradar.matching.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.common.exception.BusinessRuleException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.common.exception.ServiceUnavailableException;
import com.naukriradar.matching.ai.AiResult;
import com.naukriradar.matching.ai.AiRouter;
import com.naukriradar.matching.ai.AiUnavailableException;
import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.client.MatchingProfile;
import com.naukriradar.matching.config.AsyncConfig;
import com.naukriradar.matching.config.EvalProperties;
import com.naukriradar.matching.config.SemanticProperties;
import com.naukriradar.matching.dto.request.EvalRunRequest;
import com.naukriradar.matching.dto.response.EvalRunResponse;
import com.naukriradar.matching.embedding.EmbeddingService;
import com.naukriradar.matching.embedding.EmbeddingTexts;
import com.naukriradar.matching.embedding.Embeddings;
import com.naukriradar.matching.embedding.Vectors;
import com.naukriradar.matching.eval.EvalMetrics;
import com.naukriradar.matching.model.EvalCase;
import com.naukriradar.matching.model.EvalKind;
import com.naukriradar.matching.model.EvalRun;
import com.naukriradar.matching.repository.EvalRunRepository;
import com.naukriradar.matching.scoring.MatchContext;
import com.naukriradar.matching.scoring.MatchScorer;
import com.naukriradar.matching.scoring.SemanticVectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * Runs evals over the golden set in the background (202, then poll). Two kinds:
 * <ul>
 * <li>MATCHER: the same cases scored keyword-only and hybrid (keyword + semantic); the
 * numbers say whether semantic matching helps. Free: no AI call, local embeddings at worst.</li>
 * <li>PROMPT: one prompt version through the real providers; passes when its scores are close
 * enough to the expected ones and nearly every case got a usable answer. A pass unlocks
 * activating that version.</li>
 * </ul>
 */
@Service
public class EvalRunner {

	private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);

	private static final Set<String> KEYWORD_ONLY = Set.of("semantic");

	/** With no usable answer after this many cases, AI is down: stop instead of trying them all. */
	private static final int GIVE_UP_AFTER = 3;

	private final EvalRunRepository runs;
	private final EvalCaseService cases;
	private final MatchScorer scorer;
	private final EmbeddingService embeddings;
	private final SemanticProperties semantic;
	private final AiRouter router;
	private final PromptService prompts;
	private final EvalProperties properties;
	private final JsonMapper json;
	private final TaskExecutor executor;
	private final Clock clock = Clock.systemUTC();

	public EvalRunner(EvalRunRepository runs, EvalCaseService cases, MatchScorer scorer, EmbeddingService embeddings,
			SemanticProperties semantic, AiRouter router, PromptService prompts, EvalProperties properties, JsonMapper json,
			@Qualifier(AsyncConfig.EVAL_EXECUTOR) TaskExecutor executor) {
		this.runs = runs;
		this.cases = cases;
		this.scorer = scorer;
		this.embeddings = embeddings;
		this.semantic = semantic;
		this.router = router;
		this.prompts = prompts;
		this.properties = properties;
		this.json = json;
		this.executor = executor;
	}

	public EvalRunResponse start(EvalRunRequest request) {
		String code = null;
		Integer version = null;
		if (request.kind() == EvalKind.PROMPT) {
			code = request.promptCode() == null ? null : request.promptCode().strip();
			if (code == null || code.isEmpty()) {
				throw new BadRequestException("promptCode is needed for a PROMPT eval.");
			}
			if (!properties.gates(code)) {
				throw new BadRequestException("Only these prompts can be evaluated on the golden set: " + properties.gatedPrompts()
						+ ". They take a profile and a job and answer with a score.");
			}
			version = request.promptVersion() == null ? prompts.newestVersion(code) : request.promptVersion();
			prompts.version(code, version);
		}
		List<EvalCase> golden = cases.all();
		if (golden.isEmpty()) {
			throw new BusinessRuleException("The golden set is empty. Add cases under /api/v1/admin/evals/cases first.");
		}
		EvalRun run = runs.saveAndFlush(new EvalRun(request.kind(), code, version, golden.size(), clock.instant()));
		try {
			executor.execute(() -> execute(run.getId()));
		}
		catch (TaskRejectedException ex) {
			run.fail("Not started: other evals were running.", clock.instant());
			runs.save(run);
			throw new ServiceUnavailableException("Other evals are running. Try again when they finish.");
		}
		return toResponse(run);
	}

	public EvalRunResponse get(String id) {
		return toResponse(runs.findById(id).orElseThrow(() -> new NotFoundException("No eval run " + id + ".")));
	}

	public List<EvalRunResponse> recent(int limit) {
		return runs.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).stream().map(this::toResponse).toList();
	}

	void execute(String runId) {
		EvalRun run = runs.findById(runId).orElse(null);
		if (run == null) {
			return;
		}
		run.start();
		runs.save(run);
		try {
			List<EvalCase> golden = cases.all();
			Outcome outcome = run.getKind() == EvalKind.MATCHER ? matcher(golden)
					: prompt(run.getPromptCode(), run.getPromptVersion(), golden);
			run.succeed(outcome.passed(), json.writeValueAsString(outcome.metrics()), clock.instant());
			log.info("Eval {} ({}) finished: {}", runId, run.getKind(), outcome.passed() ? "passed" : "did not pass");
		}
		catch (RuntimeException ex) {
			log.warn("Eval {} failed", runId, ex);
			run.fail(ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(), clock.instant());
		}
		runs.save(run);
	}

	/** Keyword-only against hybrid on the same cases; passes when semantic matching doesn't make the scores worse. */
	private Outcome matcher(List<EvalCase> golden) {
		Instant now = clock.instant();
		List<MatchingProfile> profiles = new ArrayList<>();
		List<CandidateJob> jobs = new ArrayList<>();
		List<String> texts = new ArrayList<>();
		for (EvalCase evalCase : golden) {
			MatchingProfile profile = cases.profileOf(evalCase);
			CandidateJob job = cases.jobOf(evalCase, now);
			profiles.add(profile);
			jobs.add(job);
			texts.add(EmbeddingTexts.profile(profile));
			texts.add(EmbeddingTexts.job(job));
		}
		Embeddings embedded = embeddings.embed(texts);
		SemanticProperties.Range range = semantic.range(embedded.modelKey());

		List<Integer> expected = new ArrayList<>();
		List<Integer> keyword = new ArrayList<>();
		List<Integer> hybrid = new ArrayList<>();
		List<Map<String, Object>> rows = new ArrayList<>();
		for (int i = 0; i < golden.size(); i++) {
			float[] profileVector = embedded.vectors().get(2 * i);
			float[] jobVector = embedded.vectors().get(2 * i + 1);
			CandidateJob job = jobs.get(i);
			MatchContext context = MatchContext.of(profiles.get(i), now,
					new SemanticVectors(embedded.modelKey(), profileVector, Map.of(job.id(), jobVector), range));
			int keywordScore = scorer.score(context, job, KEYWORD_ONLY).total();
			int hybridScore = scorer.score(context, job).total();
			expected.add(golden.get(i).getExpectedScore());
			keyword.add(keywordScore);
			hybrid.add(hybridScore);
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("name", golden.get(i).getName());
			row.put("expected", golden.get(i).getExpectedScore());
			row.put("keyword", keywordScore);
			row.put("hybrid", hybridScore);
			row.put("cosine", Math.round(Vectors.cosine(profileVector, jobVector) * 1000) / 1000.0);
			rows.add(row);
		}
		EvalMetrics keywordMetrics = EvalMetrics.of(expected, keyword, properties.relevantScore());
		EvalMetrics hybridMetrics = EvalMetrics.of(expected, hybrid, properties.relevantScore());
		boolean passed = hybridMetrics.mae() <= keywordMetrics.mae();

		Map<String, Object> metrics = new LinkedHashMap<>();
		metrics.put("embeddingModel", embedded.modelKey());
		metrics.put("embeddingFellBack", embedded.fellBack());
		metrics.put("keyword", keywordMetrics);
		metrics.put("hybrid", hybridMetrics);
		metrics.put("verdict", String.format("Hybrid is off by %.1f points on average, keyword-only by %.1f: semantic matching %s.",
				hybridMetrics.mae(), keywordMetrics.mae(), passed ? "helps" : "hurts with this model and calibration"));
		metrics.put("cases", rows);
		return new Outcome(passed, metrics);
	}

	/** A prompt version against the expected scores, through the real providers. */
	private Outcome prompt(String code, int version, List<EvalCase> golden) {
		Instant now = clock.instant();
		List<Integer> expected = new ArrayList<>();
		List<Integer> predicted = new ArrayList<>();
		List<Map<String, Object>> rows = new ArrayList<>();
		long costMicros = 0;
		long latency = 0;
		int failures = 0;
		String lastProblem = null;
		for (EvalCase evalCase : golden) {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("name", evalCase.getName());
			row.put("expected", evalCase.getExpectedScore());
			Map<String, String> variables = Map.of(
					"profile", AiReRanker.describe(cases.profileOf(evalCase)),
					"job", AiReRanker.describe(cases.jobOf(evalCase, now)));
			try {
				AiResult result = router.runVersion(code, version, variables);
				int score = result.json().path("score").asInt();
				expected.add(evalCase.getExpectedScore());
				predicted.add(score);
				costMicros += result.costMicros();
				latency += result.latencyMs();
				row.put("ai", score);
				row.put("answeredBy", result.provider() + ":" + result.model());
			}
			catch (AiUnavailableException ex) {
				failures++;
				lastProblem = ex.getMessage();
				row.put("ai", null);
				row.put("problem", ex.getMessage());
				if (predicted.isEmpty() && failures >= GIVE_UP_AFTER) {
					throw new IllegalStateException("AI gave no usable answer to the first " + failures + " cases: " + lastProblem);
				}
			}
			rows.add(row);
		}
		if (predicted.isEmpty()) {
			throw new IllegalStateException("AI gave no usable answer: " + lastProblem);
		}
		double validRate = (double) predicted.size() / golden.size();
		EvalMetrics ai = EvalMetrics.of(expected, predicted, properties.relevantScore());
		boolean passed = validRate >= properties.minValidRate() && ai.mae() <= properties.maxMae();

		Map<String, Object> metrics = new LinkedHashMap<>();
		metrics.put("prompt", code);
		metrics.put("version", version);
		metrics.put("ai", ai);
		metrics.put("validRate", Math.round(validRate * 1000) / 1000.0);
		metrics.put("costUsd", BigDecimal.valueOf(costMicros).movePointLeft(6));
		metrics.put("averageLatencyMs", latency / predicted.size());
		metrics.put("passWhen", Map.of("maxMae", properties.maxMae(), "minValidRate", properties.minValidRate()));
		metrics.put("cases", rows);
		return new Outcome(passed, metrics);
	}

	private EvalRunResponse toResponse(EvalRun run) {
		return new EvalRunResponse(run.getId(), run.getKind(), run.getPromptCode(), run.getPromptVersion(), run.getStatus(),
				run.getPassed(), run.getCaseCount(), run.getMetricsJson() == null ? null : json.readTree(run.getMetricsJson()),
				run.getError(), run.getCreatedAt(), run.getFinishedAt());
	}

	private record Outcome(boolean passed, Map<String, Object> metrics) {
	}

}
