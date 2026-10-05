package com.naukriradar.core.dto.response;

import java.util.List;

/**
 * @param answeredBy provider:model when AI answered any question, else null
 * @param note why AI wasn't used, or null
 */
public record ScreeningAnswersResponse(String applicationId, List<Answer> answers, String answeredBy, String note) {

	/** Where an answer came from. */
	public enum Source {
		/** straight from a profile field: notice period, location, salary... */
		PROFILE,
		/** written by AI from the resume excerpts */
		AI,
		/** nothing to answer from: the user has to */
		NONE
	}

	/**
	 * @param answer null when there's nothing to answer from
	 * @param needsYou the user should answer or check it before submitting
	 * @param evidence the resume excerpts that bear on the question, for the user to answer from
	 */
	public record Answer(String question, String answer, Source source, boolean needsYou, List<String> evidence) {
	}

}
