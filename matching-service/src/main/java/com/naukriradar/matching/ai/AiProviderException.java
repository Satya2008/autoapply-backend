package com.naukriradar.matching.ai;

/** A provider's answer could not be read. Not retried: it would fail the same way. */
public class AiProviderException extends RuntimeException {

	public AiProviderException(String message) {
		super(message);
	}

	public AiProviderException(String message, Throwable cause) {
		super(message, cause);
	}

}
