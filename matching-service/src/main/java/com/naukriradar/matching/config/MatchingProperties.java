package com.naukriradar.matching.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param weights how much each scoring factor counts, by factor name; checked against the
 * factors at startup so a typo fails fast instead of silently weighing zero
 * @param minStoreScore matches below this are not kept
 * @param candidateLimit how many jobs job-service shortlists per run
 * @param candidateDays only jobs posted within this many days are considered
 * @param workerThreads match runs that can execute at once
 * @param queueCapacity runs that can wait for a free worker before new ones are refused
 * @param aiRerankTop how many of the best local matches the AI reviews per run; 0 turns it off
 */
@ConfigurationProperties("naukriradar.matching")
public record MatchingProperties(
		Map<String, Integer> weights,
		@DefaultValue("20") int minStoreScore,
		@DefaultValue("300") int candidateLimit,
		@DefaultValue("60") int candidateDays,
		@DefaultValue("2") int workerThreads,
		@DefaultValue("20") int queueCapacity,
		@DefaultValue("10") int aiRerankTop) {

	public MatchingProperties {
		weights = weights == null ? Map.of() : Map.copyOf(weights);
		if (minStoreScore < 0 || minStoreScore > 100) {
			throw new IllegalArgumentException("minStoreScore must be 0-100");
		}
		if (workerThreads < 1 || queueCapacity < 0) {
			throw new IllegalArgumentException("Need at least one worker thread");
		}
	}

}
