package com.naukriradar.common.exception;

/** The request is malformed in a way bean validation cannot express. Mapped to 400. */
public class BadRequestException extends RuntimeException {

	public BadRequestException(String message) {
		super(message);
	}

	public BadRequestException(String message, Throwable cause) {
		super(message, cause);
	}

}
