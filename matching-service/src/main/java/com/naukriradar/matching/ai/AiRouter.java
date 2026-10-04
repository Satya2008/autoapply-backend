package com.naukriradar.matching.ai;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.common.resilience.DependencyUnavailableException;
import com.naukriradar.common.resilience.Resilience;
import com.naukriradar.matching.config.AiProperties;
import com.naukriradar.matching.model.AiProviderType;
import com.naukriradar.matching.service.AiProviderService;
import com.naukriradar.matching.service.AiUsageService;
import com.naukriradar.matching.service.PromptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

/**
 * Sends a request to the ready providers, in the admin's order, until one gives a usable
 * answer. Nothing here knows a vendor or a model: they come from the provider list.
 * Each provider call goes through retry, circuit breaker and bulkhead; an answer that doesn't
 * match the schema counts as a failure and the next provider is tried. Every answer is
 * recorded with its cost, usable or not.
 */
@Service
public class AiRouter {

	private static final Logger log = LoggerFactory.getLogger(AiRouter.class);

	private static final int MAX_TOKENS = 1024;

	private final AiProperties properties;
	private final AiProviderService providers;
	private final Map<AiProviderType, AiClient> clients = new EnumMap<>(AiProviderType.class);
	private final Resilience resilience;
	private final StructuredOutputValidator validator;
	private final AiUsageService usage;
	private final AiBudgetGuard budget;
	private final PromptService prompts;
	private final AiResultCache cache;

	public AiRouter(AiProperties properties, AiProviderService providers, List<AiClient> clients, Resilience resilience,
			StructuredOutputValidator validator, AiUsageService usage, AiBudgetGuard budget, PromptService prompts,
			AiResultCache cache) {
		this.properties = properties;
		this.providers = providers;
		clients.forEach(client -> this.clients.put(client.type(), client));
		this.resilience = resilience;
		this.validator = validator;
		this.usage = usage;
		this.budget = budget;
		this.prompts = prompts;
		this.cache = cache;
	}

	/**
	 * Runs the active version of a prompt. The same question (prompt version and filled-in
	 * text) is answered from the cache: free, and it doesn't count against the budget.
	 *
	 * @throws AiUnavailableException if AI is off, the budget is spent or no provider answered
	 */
	public AiResult run(String promptCode, Map<String, String> variables, String userId) {
		return run(promptCode, variables, userId, false);
	}

	/** @param fresh skip the cache, for "write it again" */
	public AiResult run(String promptCode, Map<String, String> variables, String userId, boolean fresh) {
		ActivePrompt prompt = prompts.active(promptCode);
		AiRequest request = request(prompt, variables, userId);
		String key = AiResultCache.key(prompt, request);
		if (!fresh && properties.enabled()) {
			AiResult cached = cache.get(key);
			if (cached != null) {
				return cached;
			}
		}
		AiResult result = complete(request, providers.targets(properties.strongPurposes().contains(promptCode)));
		cache.put(key, result);
		return result;
	}

	/**
	 * Like {@link #run}, but only one provider is tried, enabled or not: for testing a
	 * provider before switching it on. A model given here overrides the provider's own.
	 */
	public AiResult runWith(String promptCode, Map<String, String> variables, String provider, String model) {
		AiProviderService.Target target = providers.target(provider);
		if (target.connection().type().needsApiKey() && target.connection().apiKey() == null) {
			throw new AiUnavailableException("Provider " + provider + " has no API key.");
		}
		if (model != null && !model.isBlank()) {
			target = new AiProviderService.Target(target.connection(), model.strip(), null, null);
		}
		return complete(request(prompts.active(promptCode), variables, null), List.of(target));
	}

	/** The models a provider's account can use, straight from the vendor. */
	public List<String> models(String provider) {
		ProviderConnection connection = providers.target(provider).connection();
		if (connection.type().needsApiKey() && connection.apiKey() == null) {
			throw new AiUnavailableException("Provider " + provider + " has no API key.");
		}
		try {
			return resilience.call("ai-" + provider, () -> clients.get(connection.type()).listModels(connection));
		}
		catch (RuntimeException ex) {
			throw new AiUnavailableException("Could not list the models of " + provider + ": " + describe(ex));
		}
	}

	private AiRequest request(ActivePrompt prompt, Map<String, String> variables, String userId) {
		String promptCode = prompt.code();
		String text;
		try {
			text = PromptService.render(prompt.template(), variables);
		}
		catch (IllegalArgumentException ex) {
			throw new BadRequestException("Prompt " + promptCode + ": " + ex.getMessage());
		}
		return new AiRequest(promptCode, userId, prompt.system(), text, prompt.outputSchema(), MAX_TOKENS);
	}

	private AiResult complete(AiRequest request, List<AiProviderService.Target> targets) {
		if (!properties.enabled()) {
			throw new AiUnavailableException("AI is switched off.");
		}
		budget.check(request.userId());
		List<String> problems = new ArrayList<>();
		int failures = 0;
		for (AiProviderService.Target target : targets) {
			ProviderConnection connection = target.connection();
			String provider = connection.name();
			AiClient client = clients.get(connection.type());
			long started = System.nanoTime();
			AiCompletion completion;
			try {
				completion = resilience.call("ai-" + provider, () -> client.complete(connection, target.model(), request));
			}
			catch (RuntimeException ex) {
				log.warn("AI provider {} failed for {}: {}", target, request.purpose(), describe(ex));
				problems.add(target + ": " + describe(ex));
				failures++;
				continue;
			}
			long latency = (System.nanoTime() - started) / 1_000_000;
			long cost = usage.cost(target, completion.tokensIn(), completion.tokensOut());
			JsonNode answer = null;
			if (request.outputSchema() != null) {
				try {
					answer = validator.validate(completion.text(), request.outputSchema());
				}
				catch (InvalidAiOutputException ex) {
					usage.record(request.userId(), request.purpose(), provider, target.model(), completion.tokensIn(),
							completion.tokensOut(), cost, latency, false);
					log.warn("AI provider {} gave an unusable answer for {}: {}", target, request.purpose(), ex.getMessage());
					problems.add(target + ": " + ex.getMessage());
					failures++;
					continue;
				}
			}
			usage.record(request.userId(), request.purpose(), provider, target.model(), completion.tokensIn(),
					completion.tokensOut(), cost, latency, true);
			return new AiResult(provider, target.model(), completion.text(), answer, completion.tokensIn(),
					completion.tokensOut(), cost, latency, failures);
		}
		throw new AiUnavailableException(problems.isEmpty()
				? "No AI provider is ready. Add one with an API key (or Ollama) under /api/v1/admin/ai/providers."
				: "No AI provider could answer: " + String.join("; ", problems));
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

}
