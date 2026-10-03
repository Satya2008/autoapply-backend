package com.naukriradar.job.model;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Fields a source's field mapping can fill. The key is what admins write in the mapping. */
@Getter
@RequiredArgsConstructor
public enum JobField {

	EXTERNAL_ID("externalId", true),
	TITLE("title", true),
	COMPANY("company", true),
	APPLY_URL("applyUrl", true),
	LOCATION("location", false),
	REMOTE("remote", false),
	DESCRIPTION("description", false),
	POSTED_AT("postedAt", false),
	SALARY_MIN("salaryMin", false),
	SALARY_MAX("salaryMax", false),
	CURRENCY("currency", false);

	private static final Map<String, JobField> BY_KEY = Arrays.stream(values())
			.collect(Collectors.toMap(JobField::getKey, Function.identity()));

	private final String key;

	private final boolean required;

	public static Optional<JobField> fromKey(String key) {
		return Optional.ofNullable(BY_KEY.get(key));
	}

}
