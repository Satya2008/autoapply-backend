package com.naukriradar.common.exception;

/** An uploaded body is over the allowed size. Mapped to 413. */
public class PayloadTooLargeException extends RuntimeException {

	public PayloadTooLargeException(String message) {
		super(message);
	}

}
