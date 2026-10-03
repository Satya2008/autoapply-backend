package com.naukriradar.matching.scoring;

/**
 * One factor's verdict on one job.
 *
 * @param score 0 (no fit) to 1 (perfect fit); 0.5 means "can't tell", used when data is missing
 * @param detail a short reason a user can read
 */
public record FactorResult(double score, String detail) {

	public static final double UNKNOWN = 0.5;

	public FactorResult {
		if (Double.isNaN(score)) {
			score = UNKNOWN;
		}
		score = Math.max(0, Math.min(1, score));
	}

	public static FactorResult unknown(String detail) {
		return new FactorResult(UNKNOWN, detail);
	}

}
