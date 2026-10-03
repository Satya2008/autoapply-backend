package com.naukriradar.core.client;

/** Another service couldn't give us what a run needs. The message is shown to the user. */
public class UpstreamException extends RuntimeException {

	public UpstreamException(String message, Throwable cause) {
		super(message, cause);
	}

}
