package com.naukriradar.matching.config;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Semantic matching: job and profile texts as vectors, compared by meaning.
 *
 * @param enabled off means keyword matching only, as before
 * @param candidateTop how many jobs the vector search adds to the keyword shortlist
 * @param maxIndexedJobs cap on vectors held in memory per instance
 * @param indexPageSize jobs fetched and embedded per step while catching up
 * @param indexOnStartup catch up on new jobs right after start
 * @param nightlyCron when the nightly batch (index catch-up + rematch) runs; "-" turns it off
 * @param calibration per embedding model, the cosine range mapped onto a 0-1 factor score:
 *     every model spreads similarities differently, so each needs its own range (tune it with
 *     the matcher eval)
 */
@ConfigurationProperties("naukriradar.semantic")
public record SemanticProperties(
		@DefaultValue("true") boolean enabled,
		@DefaultValue("100") int candidateTop,
		@DefaultValue("20000") int maxIndexedJobs,
		@DefaultValue("200") int indexPageSize,
		@DefaultValue("true") boolean indexOnStartup,
		@DefaultValue("0 30 2 * * *") String nightlyCron,
		@DefaultValue("Asia/Kolkata") String nightlyZone,
		@DefaultValue Map<String, Range> calibration) {

	/** For models nobody calibrated yet: a range typical of hosted embedding models. */
	static final Range DEFAULT_RANGE = new Range(0.20, 0.60);

	public SemanticProperties {
		if (candidateTop < 0 || maxIndexedJobs < 1 || indexPageSize < 1 || indexPageSize > 500) {
			throw new IllegalArgumentException("Check naukriradar.semantic: candidate-top >= 0, max-indexed-jobs >= 1,"
					+ " index-page-size 1-500");
		}
		calibration = calibration == null ? Map.of() : Map.copyOf(calibration);
	}

	public Range range(String modelKey) {
		return calibration.getOrDefault(modelKey, DEFAULT_RANGE);
	}

	/** Cosine at or below {@code floor} scores 0, at or above {@code ceiling} scores 1, linear between. */
	public record Range(double floor, double ceiling) {

		public Range {
			if (!(ceiling > floor)) {
				throw new IllegalArgumentException("A calibration ceiling must be above its floor");
			}
		}

		public double scale(double cosine) {
			return Math.max(0, Math.min(1, (cosine - floor) / (ceiling - floor)));
		}

	}

}
