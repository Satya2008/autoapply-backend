package com.naukriradar.job.provider;

import java.util.Map;

import com.naukriradar.job.model.JobField;

/** One item from a board, mapped to our fields but not yet cleaned. Values may be null. */
public record RawJob(Map<JobField, Object> values) {

	public Object get(JobField field) {
		return values.get(field);
	}

}
