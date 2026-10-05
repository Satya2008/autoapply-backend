package com.naukriradar.matching.config;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.naukriradar.matching.model.AiProviderType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * AI setup. Providers are managed at runtime through the admin API and stored in the
 * database; the providers listed here are only added on the first start, so keys given as
 * environment variables work without any clicking.
 *
 * @param dailyBudgetUsd what one user's AI calls may cost per day (UTC)
 * @param seedProviders added once, in this order, when no provider exists yet
 * @param prices model -> USD per million tokens, for providers without their own prices
 * @param strongPurposes prompts that use each provider's strong model (writing, not parsing)
 */
@ConfigurationProperties("naukriradar.ai")
public record AiProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("0.50") BigDecimal dailyBudgetUsd,
		@DefaultValue Map<String, SeedProvider> seedProviders,
		@DefaultValue Map<String, Price> prices,
		@DefaultValue({ "cover-letter", "cover-letter-rag", "screening-answers" }) List<String> strongPurposes) {

	public record SeedProvider(AiProviderType type, String baseUrl, String apiKey, String model, String embeddingModel) {
	}

	/** USD per million tokens, which is the same number as micro-dollars per token. */
	public record Price(BigDecimal input, BigDecimal output) {
	}

}
