package com.naukriradar.job.exception;

import com.naukriradar.common.resilience.TransientFailure;

/** The board answered 5xx or 429: it may well work on the next try, so this one is retried. */
public class BoardUnavailableException extends JobSourceFetchException implements TransientFailure {

	public BoardUnavailableException(String message) {
		super(message);
	}

}
