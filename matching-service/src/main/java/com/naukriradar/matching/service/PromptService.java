package com.naukriradar.matching.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.matching.ai.ActivePrompt;
import com.naukriradar.matching.dto.request.PromptVersionRequest;
import com.naukriradar.matching.dto.response.PromptResponse;
import com.naukriradar.matching.model.Prompt;
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
	private final JsonMapper json;
	private final Clock clock = Clock.systemUTC();

	public PromptService(PromptRepository repository, JsonMapper json) {
		this.repository = repository;
		this.json = json;
	}

	@Transactional(readOnly = true)
	public ActivePrompt active(String code) {
		Prompt prompt = repository.findByCodeAndActiveTrue(code)
				.orElseThrow(() -> new NotFoundException("No active prompt " + code + "."));
		Map<String, Object> schema = prompt.getOutputSchema() == null ? null : json.readValue(prompt.getOutputSchema(), SCHEMA);
		return new ActivePrompt(prompt.getCode(), prompt.getVersion(), prompt.getSystem(), prompt.getTemplate(), schema);
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
		return repository.findAllByOrderByCodeAscVersionDesc().stream().map(PromptService::toResponse).toList();
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
			prompt.activate();
		}
		try {
			return toResponse(repository.saveAndFlush(prompt));
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("Someone else added a version of " + code + " just now. Try again.");
		}
	}

	@Transactional
	public PromptResponse activate(String code, int version) {
		Prompt chosen = repository.findByCodeAndVersion(code, version)
				.orElseThrow(() -> new NotFoundException("No version " + version + " of prompt " + code + "."));
		repository.findByCodeOrderByVersionDesc(code).forEach(Prompt::deactivate);
		chosen.activate();
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
	}

	private static PromptResponse toResponse(Prompt prompt) {
		return new PromptResponse(prompt.getCode(), prompt.getVersion(), prompt.isActive(), prompt.getSystem(),
				prompt.getTemplate(), prompt.getOutputSchema(), prompt.getCreatedAt());
	}

	private record DefaultPrompt(String code, String system, String template, Map<String, Object> outputSchema) {
	}

}
