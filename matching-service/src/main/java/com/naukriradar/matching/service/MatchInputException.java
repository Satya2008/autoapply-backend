package com.naukriradar.matching.service;

/** The profile doesn't have enough to match on. The message tells the user what to add. */
public class MatchInputException extends RuntimeException {

	public MatchInputException(String message) {
		super(message);
	}

}
