package com.naukriradar.matching.eval;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * How close predicted scores are to the expected ones, in the numbers a reviewer reads:
 * <ul>
 * <li>MAE: the average miss in points; easy to say ("off by 9 on average")</li>
 * <li>Spearman: does it put the jobs in the right order? Ranking is what the match list shows</li>
 * <li>precision / recall: of the jobs it calls matches, how many are; of the real matches, how many it found</li>
 * </ul>
 *
 * @param within15 share of cases off by 15 points or less
 * @param spearman -1 to 1; null with fewer than two cases or no spread
 */
public record EvalMetrics(int cases, double mae, double within15, Double spearman, double precision, double recall,
		double f1) {

	public static EvalMetrics of(List<Integer> expected, List<Integer> predicted, int relevantScore) {
		if (expected.size() != predicted.size()) {
			throw new IllegalArgumentException("Expected and predicted differ in size");
		}
		int n = expected.size();
		if (n == 0) {
			return new EvalMetrics(0, 0, 0, null, 0, 0, 0);
		}
		double totalError = 0;
		int close = 0;
		int truePositives = 0;
		int predictedPositives = 0;
		int actualPositives = 0;
		for (int i = 0; i < n; i++) {
			int miss = Math.abs(expected.get(i) - predicted.get(i));
			totalError += miss;
			if (miss <= 15) {
				close++;
			}
			boolean actual = expected.get(i) >= relevantScore;
			boolean guessed = predicted.get(i) >= relevantScore;
			actualPositives += actual ? 1 : 0;
			predictedPositives += guessed ? 1 : 0;
			truePositives += actual && guessed ? 1 : 0;
		}
		double precision = predictedPositives == 0 ? 0 : (double) truePositives / predictedPositives;
		double recall = actualPositives == 0 ? 0 : (double) truePositives / actualPositives;
		double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
		return new EvalMetrics(n, round(totalError / n), round((double) close / n), spearman(expected, predicted),
				round(precision), round(recall), round(f1));
	}

	/** Pearson correlation of the ranks; tied values share their average rank. */
	static Double spearman(List<Integer> a, List<Integer> b) {
		if (a.size() < 2) {
			return null;
		}
		double[] ra = ranks(a);
		double[] rb = ranks(b);
		double meanA = Arrays.stream(ra).average().orElse(0);
		double meanB = Arrays.stream(rb).average().orElse(0);
		double cov = 0;
		double varA = 0;
		double varB = 0;
		for (int i = 0; i < ra.length; i++) {
			cov += (ra[i] - meanA) * (rb[i] - meanB);
			varA += (ra[i] - meanA) * (ra[i] - meanA);
			varB += (rb[i] - meanB) * (rb[i] - meanB);
		}
		if (varA == 0 || varB == 0) {
			return null;
		}
		return round(cov / Math.sqrt(varA * varB));
	}

	private static double[] ranks(List<Integer> values) {
		Integer[] order = new Integer[values.size()];
		for (int i = 0; i < order.length; i++) {
			order[i] = i;
		}
		Arrays.sort(order, Comparator.comparingInt(values::get));
		double[] ranks = new double[values.size()];
		int i = 0;
		while (i < order.length) {
			int j = i;
			while (j + 1 < order.length && values.get(order[j + 1]).equals(values.get(order[i]))) {
				j++;
			}
			double average = (i + j) / 2.0 + 1;
			for (int k = i; k <= j; k++) {
				ranks[order[k]] = average;
			}
			i = j + 1;
		}
		return ranks;
	}

	private static double round(double value) {
		return Math.round(value * 1000) / 1000.0;
	}

}
