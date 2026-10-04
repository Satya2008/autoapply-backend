package com.naukriradar.matching.ai;

/**
 * No provider gave a usable answer, AI is off, or the user's budget is spent. Callers fall
 * back to working without AI.
 */
public class AiUnavailableException extends RuntimeException {

	public AiUnavailableException(String message) {
		super(message);
	}

}
