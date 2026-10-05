package com.naukriradar.matching.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Evals and the activation gate.
 *
 * @param gatedPrompts prompts whose new versions can only be activated after passing an eval
 *     (each must take {{profile}} and {{job}} and answer with a 0-100 "score")
 * @param maxMae a prompt passes when its scores are, on average, at most this far from the expected ones
 * @param minValidRate and when at least this share of cases got a usable answer
 * @param relevantScore expected and predicted scores at or above this count as "a match" for precision and recall
 */
@ConfigurationProperties("naukriradar.evals")
public record EvalProperties(
		@DefaultValue("job-fit") List<String> gatedPrompts,
		@DefaultValue("20") double maxMae,
		@DefaultValue("0.9") double minValidRate,
		@DefaultValue("60") int relevantScore) {

	public EvalProperties {
		gatedPrompts = gatedPrompts == null ? List.of() : List.copyOf(gatedPrompts);
		if (maxMae < 0 || minValidRate < 0 || minValidRate > 1 || relevantScore < 0 || relevantScore > 100) {
			throw new IllegalArgumentException("Check naukriradar.evals: max-mae >= 0, min-valid-rate 0-1, relevant-score 0-100");
		}
	}

	public boolean gates(String promptCode) {
		return gatedPrompts.contains(promptCode);
	}

}
