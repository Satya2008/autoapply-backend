package com.naukriradar.common.exception;

/** The requested resource does not exist. Mapped to 404. */
public class NotFoundException extends RuntimeException {

	public NotFoundException(String message) {
		super(message);
	}

}
