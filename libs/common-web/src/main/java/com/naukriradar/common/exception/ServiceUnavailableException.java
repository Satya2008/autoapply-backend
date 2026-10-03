package com.naukriradar.common.exception;

/** The service is temporarily too busy to take the request. Mapped to 503. */
public class ServiceUnavailableException extends RuntimeException {

	public ServiceUnavailableException(String message) {
		super(message);
	}

}
