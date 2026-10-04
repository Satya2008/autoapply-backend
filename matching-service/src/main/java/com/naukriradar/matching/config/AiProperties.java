package com.naukriradar.matching.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI setup. Providers are named accounts of a given type; routes say which provider and model
 * each purpose tries, in order. A provider without an API key (except Ollama, which needs
 * none) is skipped, so the app runs with no AI configured at all.
 *
 * @param dailyBudgetUsd what one user's AI calls may cost per day (UTC)
 * @param routes purpose -> ["provider:model", ...]; "default" is used for purposes not listed
 * @param prices model -> USD per million tokens. A model without a price is recorded at 0
 */
@ConfigurationProperties("naukriradar.ai")
public record AiProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("0.50") BigDecimal dailyBudgetUsd,
		@DefaultValue Map<String, Provider> providers,
		@DefaultValue Map<String, List<String>> routes,
		@DefaultValue Map<String, Price> prices) {

	public enum ProviderType {
		ANTHROPIC, OPENAI, GEMINI, OLLAMA
	}

	public record Provider(ProviderType type, String baseUrl, String apiKey, @DefaultValue("30s") Duration timeout) {

		public boolean configured() {
			return baseUrl != null && !baseUrl.isBlank()
					&& (type == ProviderType.OLLAMA || apiKey != null && !apiKey.isBlank());
		}

	}

	/** USD per million tokens, which is the same number as micro-dollars per token. */
	public record Price(BigDecimal input, BigDecimal output) {
	}

}
