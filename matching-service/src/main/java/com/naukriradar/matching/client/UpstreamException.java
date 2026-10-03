package com.naukriradar.matching.client;

/** Another service couldn't give us what a match run needs. The message is shown to the user. */
public class UpstreamException extends RuntimeException {

	public UpstreamException(String message) {
		super(message);
	}

	public UpstreamException(String message, Throwable cause) {
		super(message, cause);
	}

}
