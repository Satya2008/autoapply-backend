package com.naukriradar.job.normalizer;

/** Either a usable job or the reason the item was dropped. */
public record NormalizationResult(NormalizedJob job, String skipReason) {

	static NormalizationResult ok(NormalizedJob job) {
		return new NormalizationResult(job, null);
	}

	static NormalizationResult skip(String reason) {
		return new NormalizationResult(null, reason);
	}

	public boolean isOk() {
		return job != null;
	}

}
