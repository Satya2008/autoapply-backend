package com.naukriradar.matching.scoring;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.config.MatchingProperties;
import org.springframework.stereotype.Component;

/**
 * Weighted average of every factor, scaled to 0-100. Weights live in configuration, and are
 * checked against the factors at startup: a missing, unknown or negative weight stops the
 * service rather than quietly skewing every score.
 */
@Component
public class MatchScorer {

	private final List<ScoringFactor> factors;

	private final Map<String, Integer> weights;

	private final int totalWeight;

	public MatchScorer(List<ScoringFactor> factors, MatchingProperties properties) {
		this.factors = List.copyOf(factors);
		this.weights = properties.weights();
		Set<String> names = new HashSet<>();
		for (ScoringFactor factor : factors) {
			if (!names.add(factor.name())) {
				throw new IllegalStateException("Two scoring factors are called " + factor.name());
			}
			Integer weight = weights.get(factor.name());
			if (weight == null || weight < 0) {
				throw new IllegalStateException("Set naukriradar.matching.weights." + factor.name() + " to 0 or more");
			}
		}
		for (String configured : weights.keySet()) {
			if (!names.contains(configured)) {
				throw new IllegalStateException("naukriradar.matching.weights." + configured + " matches no scoring factor");
			}
		}
		this.totalWeight = factors.stream().mapToInt(f -> weights.get(f.name())).sum();
		if (totalWeight <= 0) {
			throw new IllegalStateException("Scoring weights add up to 0");
		}
	}

	public ScoreResult score(MatchContext context, CandidateJob job) {
		return score(context, job, Set.of());
	}

	/**
	 * Scores as if the named factors didn't exist: the others share the full 100. The eval
	 * uses it to compare keyword-only scoring with hybrid scoring on the same cases.
	 */
	public ScoreResult score(MatchContext context, CandidateJob job, Set<String> leaveOut) {
		int total = leaveOut.isEmpty() ? totalWeight : factors.stream()
				.filter(f -> !leaveOut.contains(f.name()))
				.mapToInt(f -> weights.get(f.name()))
				.sum();
		if (total <= 0) {
			throw new IllegalArgumentException("Leaving out " + leaveOut + " leaves no weight to score with");
		}
		List<FactorScore> breakdown = new ArrayList<>(factors.size());
		double sum = 0;
		for (ScoringFactor factor : factors) {
			if (leaveOut.contains(factor.name())) {
				continue;
			}
			int weight = weights.get(factor.name());
			FactorResult result = factor.score(context, job);
			double points = 100.0 * weight * result.score() / total;
			sum += points;
			breakdown.add(new FactorScore(factor.name(), weight, round(result.score(), 2), round(points, 1), result.detail()));
		}
		breakdown.sort((a, b) -> Double.compare(b.points(), a.points()));
		return new ScoreResult((int) Math.round(Math.max(0, Math.min(100, sum))), breakdown);
	}

	private static double round(double value, int places) {
		double scale = Math.pow(10, places);
		return Math.round(value * scale) / scale;
	}

}
