package com.naukriradar.matching.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A golden-set example.
 *
 * @param expectedScore what a careful recruiter would score this pair, 0-100
 */
public record EvalCaseRequest(
		@NotBlank @Size(max = 100) String name,
		@NotNull @Valid Profile profile,
		@NotNull @Valid Job job,
		@NotNull @Min(0) @Max(100) Integer expectedScore,
		@Size(max = 500) String notes) {

	public record Profile(
			@Size(max = 50) List<@NotBlank @Size(max = 50) String> skills,
			@Size(max = 10) List<@NotBlank @Size(max = 100) String> targetRoles,
			@Min(0) @Max(50) Integer experienceYears,
			@Size(max = 10) List<@NotBlank @Size(max = 100) String> preferredLocations,
			Boolean remoteOk) {

		public Profile {
			skills = skills == null ? List.of() : List.copyOf(skills);
			targetRoles = targetRoles == null ? List.of() : List.copyOf(targetRoles);
			preferredLocations = preferredLocations == null ? List.of() : List.copyOf(preferredLocations);
		}

	}

	public record Job(
			@NotBlank @Size(max = 300) String title,
			@Size(max = 200) String company,
			@Size(max = 200) String location,
			Boolean remote,
			@Size(max = 20000) String description,
			@Size(max = 50) List<@NotBlank @Size(max = 50) String> requiredSkills,
			@Min(0) @Max(40) Integer minYearsExperience,
			@Size(max = 20) String seniority) {

		public Job {
			requiredSkills = requiredSkills == null ? List.of() : List.copyOf(requiredSkills);
		}

	}

}
