package com.naukriradar.job.exception;

/**
 * Calling a board or reading its answer failed. The message is safe to show an admin and
 * ends up in the source's last run message.
 */
public class JobSourceFetchException extends RuntimeException {

	public JobSourceFetchException(String message) {
		super(message);
	}

	public JobSourceFetchException(String message, Throwable cause) {
		super(message, cause);
	}

}
