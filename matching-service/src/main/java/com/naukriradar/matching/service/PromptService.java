package com.naukriradar.matching.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.common.exception.BusinessRuleException;
import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.matching.ai.ActivePrompt;
import com.naukriradar.matching.config.EvalProperties;
import com.naukriradar.matching.dto.request.PromptVersionRequest;
import com.naukriradar.matching.dto.response.PromptResponse;
import com.naukriradar.matching.model.EvalKind;
import com.naukriradar.matching.model.EvalRunStatus;
import com.naukriradar.matching.model.Prompt;
import com.naukriradar.matching.repository.EvalRunRepository;
import com.naukriradar.matching.repository.PromptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Prompts live in the database, versioned, so wording can change without a deploy and a bad
 * change is undone by activating the previous version.
 */
@Service
public class PromptService {

	private static final Logger log = LoggerFactory.getLogger(PromptService.class);

	private static final Pattern CODE = Pattern.compile("[a-z0-9][a-z0-9-]{0,49}");

	private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}");

	private static final TypeReference<Map<String, Object>> SCHEMA = new TypeReference<>() {
	};

	private final PromptRepository repository;
	private final EvalRunRepository evalRuns;
	private final EvalProperties evals;
	private final JsonMapper json;
	private final Clock clock = Clock.systemUTC();

	public PromptService(PromptRepository repository, EvalRunRepository evalRuns, EvalProperties evals, JsonMapper json) {
		this.repository = repository;
		this.evalRuns = evalRuns;
		this.evals = evals;
		this.json = json;
	}

	@Transactional(readOnly = true)
	public ActivePrompt active(String code) {
		return toActive(repository.findByCodeAndActiveTrue(code)
				.orElseThrow(() -> new NotFoundException("No active prompt " + code + ".")));
	}

	/** Any version, active or not. */
	@Transactional(readOnly = true)
	public ActivePrompt version(String code, int version) {
		return toActive(load(code, version));
	}

	/** The newest version of a prompt, active or not. */
	@Transactional(readOnly = true)
	public int newestVersion(String code) {
		int newest = repository.maxVersion(code);
		if (newest <= 0) {
			throw new NotFoundException("No prompt " + code + ".");
		}
		return newest;
	}

	private ActivePrompt toActive(Prompt prompt) {
		Map<String, Object> schema = prompt.getOutputSchema() == null ? null : json.readValue(prompt.getOutputSchema(), SCHEMA);
		return new ActivePrompt(prompt.getCode(), prompt.getVersion(), prompt.getSystem(), prompt.getTemplate(), schema);
	}

	private Prompt load(String code, int version) {
		return repository.findByCodeAndVersion(code, version)
				.orElseThrow(() -> new NotFoundException("No version " + version + " of prompt " + code + "."));
	}

	/** Fills in {{name}} variables. A variable without a value is an error, not a blank. */
	public static String render(String template, Map<String, String> variables) {
		Matcher matcher = VARIABLE.matcher(template);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			String value = variables.get(matcher.group(1));
			if (value == null) {
				throw new IllegalArgumentException("No value for {{" + matcher.group(1) + "}}");
			}
			matcher.appendReplacement(out, Matcher.quoteReplacement(value));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	@Transactional(readOnly = true)
	public List<PromptResponse> list() {
		return repository.findAllByOrderByCodeAscVersionDesc().stream().map(this::toResponse).toList();
	}

	/** Saved inactive, so what runs today is untouched until someone activates it. The first version of a code is active. */
	@Transactional
	public PromptResponse createVersion(String code, PromptVersionRequest request) {
		if (!CODE.matcher(code).matches()) {
			throw new BadRequestException("Prompt code must be lowercase letters, digits and dashes.");
		}
		boolean first = !repository.existsByCode(code);
		String schema = request.outputSchema() == null || request.outputSchema().isEmpty() ? null
				: json.writeValueAsString(request.outputSchema());
		Prompt prompt = new Prompt(code, repository.maxVersion(code) + 1, request.system(), request.template(), schema,
				clock.instant());
		if (first) {
			prompt.activate(clock.instant());
		}
		try {
			return toResponse(repository.saveAndFlush(prompt));
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("Someone else added a version of " + code + " just now. Try again.");
		}
	}

	/**
	 * Makes this version the one in use. For gated prompts a version that has never been live
	 * must pass an eval first, so a prompt change is judged by numbers, not by feel. Going back
	 * to a version that was live before (the rollback) needs no new eval.
	 *
	 * @throws BusinessRuleException if the version still needs a passing eval
	 */
	@Transactional
	public PromptResponse activate(String code, int version) {
		Prompt chosen = load(code, version);
		if (!chosen.isActive() && chosen.getActivatedAt() == null && evals.gates(code)
				&& !evalRuns.existsByKindAndPromptCodeAndPromptVersionAndStatusAndPassedTrue(EvalKind.PROMPT, code, version,
						EvalRunStatus.SUCCEEDED)) {
			throw new BusinessRuleException("Version " + version + " of " + code + " has not passed an eval yet. Run one with"
					+ " POST /api/v1/admin/evals/runs {\"kind\": \"PROMPT\", \"promptCode\": \"" + code + "\", \"promptVersion\": "
					+ version + "} and activate it once it passes.");
		}
		repository.findByCodeOrderByVersionDesc(code).forEach(Prompt::deactivate);
		chosen.activate(clock.instant());
		return toResponse(chosen);
	}

	/** Adds the built-in prompts that aren't in the database yet. */
	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void seedDefaults() throws IOException {
		List<DefaultPrompt> defaults;
		try (InputStream in = new ClassPathResource("prompts/defaults.json").getInputStream()) {
			defaults = json.readValue(in, new TypeReference<>() {
			});
		}
		for (DefaultPrompt prompt : defaults) {
			if (!repository.existsByCode(prompt.code())) {
				createVersion(prompt.code(), new PromptVersionRequest(prompt.system(), prompt.template(), prompt.outputSchema()));
				log.info("Added built-in prompt {}", prompt.code());
			}
		}
		// versions that were live before activation times were kept count as having been live
		repository.findAll().stream().filter(p -> p.isActive() && p.getActivatedAt() == null)
				.forEach(p -> p.activate(p.getCreatedAt()));
	}

	private PromptResponse toResponse(Prompt prompt) {
		return new PromptResponse(prompt.getCode(), prompt.getVersion(), prompt.isActive(), prompt.getSystem(),
				prompt.getTemplate(), prompt.getOutputSchema(), prompt.getCreatedAt(), prompt.getActivatedAt(),
				evals.gates(prompt.getCode()));
	}

	private record DefaultPrompt(String code, String system, String template, Map<String, Object> outputSchema) {
	}

}
