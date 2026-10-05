package com.naukriradar.matching.model;

/** What an eval measures. */
public enum EvalKind {

	/** The local matcher on the golden set: keyword-only scoring against hybrid (keyword + semantic). Free. */
	MATCHER,

	/** One version of an AI prompt on the golden set, through the configured providers. Costs AI calls. */
	PROMPT

}
