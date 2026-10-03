package com.naukriradar.common.exception;

/** The request clashes with existing state, such as a duplicate email. Mapped to 409. */
public class ConflictException extends RuntimeException {

	public ConflictException(String message) {
		super(message);
	}

}
