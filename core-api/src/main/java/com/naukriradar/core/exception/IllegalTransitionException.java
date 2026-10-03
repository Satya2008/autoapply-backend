package com.naukriradar.core.exception;

import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.core.model.ApplicationStatus;

/** A status change the rules don't allow, e.g. SKIPPED to OFFER. Mapped to 409. */
public class IllegalTransitionException extends ConflictException {

	public IllegalTransitionException(ApplicationStatus from, ApplicationStatus to) {
		super("An application can't go from " + from + " to " + to + ".");
	}

	public IllegalTransitionException(String message) {
		super(message);
	}

}
