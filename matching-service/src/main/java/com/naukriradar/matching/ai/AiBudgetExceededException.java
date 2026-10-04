package com.naukriradar.matching.ai;

/** The user has used up today's AI budget. */
public class AiBudgetExceededException extends AiUnavailableException {

	public AiBudgetExceededException(String message) {
		super(message);
	}

}
