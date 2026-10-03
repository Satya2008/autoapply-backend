package com.naukriradar.common.exception;

/**
 * The input is well-formed but breaks a business rule, such as turning on auto apply
 * with no target roles. Mapped to 422.
 */
public class BusinessRuleException extends RuntimeException {

	public BusinessRuleException(String message) {
		super(message);
	}

}
