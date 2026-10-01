package com.naukriradar.common;

/** The caller could not be identified. Mapped to 401. */
public class UnauthenticatedException extends RuntimeException {

	public UnauthenticatedException(String message) {
		super(message);
	}

}
