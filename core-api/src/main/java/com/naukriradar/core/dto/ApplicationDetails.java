package com.naukriradar.core.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * What application forms ask that matching doesn't need: current company and pay, education,
 * relocation, and the questions most forms add (gender, where you heard of the job, work
 * authorization, sponsorship, disability, veteran status). Only used to fill forms; never shown
 * to employers except in a form the user sends.
 *
 * @param currentCtc yearly, in INR
 * @param expectedCtc yearly, in INR (the profile's expected salary)
 * @param gender as forms offer it ("Male", "Female", "Prefer not to say"...); null leaves it to you
 * @param heardAbout how you heard of jobs, e.g. "LinkedIn" or "Company website"
 * @param authorizedToWork authorized to work in India without a visa
 * @param needsSponsorship needs an employer to sponsor a work visa
 */
public record ApplicationDetails(
		@Size(max = 100) String currentCompany,
		@PositiveOrZero Long currentCtc,
		@PositiveOrZero Long expectedCtc,
		@Size(max = 100) String highestDegree,
		@Size(max = 100) String fieldOfStudy,
		@Size(max = 150) String college,
		@Min(1950) @Max(2100) Integer educationStartYear,
		@Min(1950) @Max(2100) Integer graduationYear,
		Boolean willingToRelocate,
		@Size(max = 50) String gender,
		@Size(max = 100) String heardAbout,
		Boolean authorizedToWork,
		Boolean needsSponsorship,
		@Size(max = 50) String disability,
		@Size(max = 50) String veteranStatus) {

	public static final ApplicationDetails EMPTY = new ApplicationDetails(null, null, null, null, null, null, null, null, null, null, null,
			null, null, null, null);

}
