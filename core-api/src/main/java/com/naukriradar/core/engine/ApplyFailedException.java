package com.naukriradar.core.engine;

/** One automatic attempt failed; the message goes in the application's lastError. */
public class ApplyFailedException extends RuntimeException {

	public ApplyFailedException(String message) {
		super(message);
	}

	public ApplyFailedException(String message, Throwable cause) {
		super(message, cause);
	}

}
