package com.naukriradar.job.exception;

import java.util.List;

import com.naukriradar.common.exception.BadRequestException;

/** A job source definition that could never work, e.g. a JsonPath that doesn't compile. */
public class InvalidSourceConfigException extends BadRequestException {

	public InvalidSourceConfigException(List<String> problems) {
		super("Invalid job source: " + String.join("; ", problems));
	}

}
