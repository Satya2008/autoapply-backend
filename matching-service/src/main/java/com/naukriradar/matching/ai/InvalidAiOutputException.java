package com.naukriradar.matching.ai;

/** The model answered, but not in the shape asked for. */
public class InvalidAiOutputException extends RuntimeException {

	public InvalidAiOutputException(String message) {
		super(message);
	}

}
