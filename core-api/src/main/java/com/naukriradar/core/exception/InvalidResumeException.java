package com.naukriradar.core.exception;

import com.naukriradar.common.exception.BadRequestException;

/** The uploaded file is not a resume we can accept or read. */
public class InvalidResumeException extends BadRequestException {

	public InvalidResumeException(String message) {
		super(message);
	}

	public InvalidResumeException(String message, Throwable cause) {
		super(message, cause);
	}

}
