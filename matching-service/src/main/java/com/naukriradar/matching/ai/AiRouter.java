package com.naukriradar.matching.ai;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.common.resilience.DependencyUnavailableException;
import com.naukriradar.common.resilience.Resilience;
import com.naukriradar.matching.config.AiProperties;
import com.naukriradar.matching.service.AiUsageService;
import com.naukriradar.matching.service.PromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Sends a request to the providers on its route, in order, until one gives a usable answer.
 * Each provider call goes through retry, circuit breaker and bulkhead; an answer that doesn't
 * match the schema counts as a failure and the next provider is tried. Every answer is
 * recorded with its cost, usable or not.
 */
@Service
public class AiRouter {

	private static final Logger log = LoggerFactory.getLogger(AiRouter.class);

	private static final int MAX_TOKENS = 1024;

	private final AiProperties properties;
	private final Map<AiProperties.ProviderType, AiClient> clients = new EnumMap<>(AiProperties.ProviderType.class);
	private final Resilience resilience;
	private final StructuredOutputValidator validator;
	private final AiUsageService usage;
	private final AiBudgetGuard budget;
	private final PromptService prompts;

	public AiRouter(AiProperties properties, List<AiClient> clients, Resilience resilience,
			StructuredOutputValidator validator, AiUsageService usage, AiBudgetGuard budget, PromptService prompts) {
		this.properties = properties;
		clients.forEach(client -> this.clients.put(client.type(), client));
		this.resilience = resilience;
		this.validator = validator;
		this.usage = usage;
		this.budget = budget;
		this.prompts = prompts;
	}

	/**
	 * Runs the active version of a prompt.
	 *
	 * @throws AiUnavailableException if AI is off, the budget is spent or no provider answered
	 */
	public AiResult run(String promptCode, Map<String, String> variables, String userId) {
		return complete(request(promptCode, variables, userId), targets(promptCode));
	}

	/** Like {@link #run}, but only the given provider and model are tried. */
	public AiResult runWith(String promptCode, Map<String, String> variables, String provider, String model) {
		AiProperties.Provider config = properties.providers().get(provider);
		if (config == null) {
			throw new BadRequestException("No AI provider " + provider + ". Known: " + properties.providers().keySet() + ".");
		}
		String chosen = model;
		if (chosen == null || chosen.isBlank()) {
			chosen = targets(promptCode).stream().filter(t -> t.provider().equals(provider)).map(Target::model).findFirst()
					.orElseThrow(() -> new BadRequestException("Say which model to use with " + provider + "."));
		}
		return complete(request(promptCode, variables, null), List.of(new Target(provider, chosen)));
	}

	private AiRequest request(String promptCode, Map<String, String> variables, String userId) {
		ActivePrompt prompt = prompts.active(promptCode);
		String text;
		try {
			text = PromptService.render(prompt.template(), variables);
		}
		catch (IllegalArgumentException ex) {
			throw new BadRequestException("Prompt " + promptCode + ": " + ex.getMessage());
		}
		return new AiRequest(promptCode, userId, prompt.system(), text, prompt.outputSchema(), MAX_TOKENS);
	}

	private AiResult complete(AiRequest request, List<Target> targets) {
		if (!properties.enabled()) {
			throw new AiUnavailableException("AI is switched off.");
		}
		budget.check(request.userId());
		List<String> problems = new ArrayList<>();
		int failures = 0;
		for (Target target : targets) {
			AiProperties.Provider config = properties.providers().get(target.provider());
			if (config == null || !config.configured()) {
				problems.add(target + ": not configured");
				continue;
			}
			AiClient client = clients.get(config.type());
			long started = System.nanoTime();
			AiCompletion completion;
			try {
				completion = resilience.call("ai-" + target.provider(), () -> client.complete(config, target.model(), request));
			}
			catch (RuntimeException ex) {
				log.warn("AI provider {} failed for {}: {}", target, request.purpose(), describe(ex));
				problems.add(target + ": " + describe(ex));
				failures++;
				continue;
			}
			long latency = (System.nanoTime() - started) / 1_000_000;
			long cost = usage.cost(target.model(), completion.tokensIn(), completion.tokensOut());
			JsonNode answer = null;
			if (request.outputSchema() != null) {
				try {
					answer = validator.validate(completion.text(), request.outputSchema());
				}
				catch (InvalidAiOutputException ex) {
					usage.record(request.userId(), request.purpose(), target.provider(), target.model(), completion.tokensIn(),
							completion.tokensOut(), cost, latency, false);
					log.warn("AI provider {} gave an unusable answer for {}: {}", target, request.purpose(), ex.getMessage());
					problems.add(target + ": " + ex.getMessage());
					failures++;
					continue;
				}
			}
			usage.record(request.userId(), request.purpose(), target.provider(), target.model(), completion.tokensIn(),
					completion.tokensOut(), cost, latency, true);
			return new AiResult(target.provider(), target.model(), completion.text(), answer, completion.tokensIn(),
					completion.tokensOut(), cost, latency, failures);
		}
		throw new AiUnavailableException(problems.isEmpty() ? "No AI provider is set up for " + request.purpose() + "."
				: "No AI provider could answer: " + String.join("; ", problems));
	}

	/** The route for a purpose: "provider:model" entries, falling back to the default route. */
	List<Target> targets(String purpose) {
		List<String> route = properties.routes().getOrDefault(purpose, properties.routes().getOrDefault("default", List.of()));
		return route.stream().map(entry -> {
			int colon = entry.indexOf(':');
			if (colon <= 0 || colon == entry.length() - 1) {
				throw new IllegalStateException("AI route entry must be provider:model, got " + entry);
			}
			return new Target(entry.substring(0, colon).strip(), entry.substring(colon + 1).strip());
		}).toList();
	}

	private static String describe(RuntimeException ex) {
		if (ex instanceof DependencyUnavailableException) {
			return "circuit open or busy, skipped";
		}
		if (ex instanceof RestClientResponseException response) {
			return "HTTP " + response.getStatusCode().value();
		}
		if (ex instanceof ResourceAccessException) {
			Throwable root = ex;
			while (root.getCause() != null) {
				root = root.getCause();
			}
			return "unreachable or timed out (" + root.getClass().getSimpleName() + ")";
		}
		return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
	}

	record Target(String provider, String model) {

		@Override
		public String toString() {
			return provider + ":" + model;
		}

	}

}
