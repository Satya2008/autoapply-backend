package com.naukriradar.matching.scoring;

import java.util.Map;

import com.naukriradar.matching.config.SemanticProperties;

/**
 * The vectors one run compares: the profile's and each job's, all from one model.
 *
 * @param range how this model's cosine maps onto a 0-1 factor score
 */
public record SemanticVectors(String modelKey, float[] profile, Map<String, float[]> jobs, SemanticProperties.Range range) {

	public static final SemanticVectors NONE = new SemanticVectors(null, null, Map.of(), null);

	public SemanticVectors {
		jobs = jobs == null ? Map.of() : Map.copyOf(jobs);
	}

	public boolean on() {
		return profile != null && profile.length > 0 && range != null;
	}

}
