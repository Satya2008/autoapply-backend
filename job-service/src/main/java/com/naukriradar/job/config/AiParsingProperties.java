package com.naukriradar.job.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Parsing jobs with AI, once per job.
 *
 * @param matchingServiceUrl where the AI lives (matching-service)
 * @param batchSize jobs parsed per round; a round runs after every fetch and on a schedule
 * @param cron when to look for unparsed jobs between fetches
 */
@ConfigurationProperties("naukriradar.jobs.parsing")
public record AiParsingProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("http://localhost:8083") String matchingServiceUrl,
		@DefaultValue("100") int batchSize,
		@DefaultValue("0 */15 * * * *") String cron,
		@DefaultValue("60s") Duration readTimeout) {
}
