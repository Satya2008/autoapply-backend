package com.naukriradar.core.dto.request;

import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

import com.naukriradar.core.dto.ApplicationDetails;
import com.naukriradar.core.dto.JobFilters;
import jakarta.validation.Valid;

/**
 * Full replacement of a profile (PUT). Optional fields left out are cleared; the four
 * settings at the end must always be sent. A list left out is treated as empty.
 */
public record UpdateProfileRequest(
		@Size(max = 100) String fullName,
		@Size(max = 20) @Pattern(regexp = "^$|^(?=[+0-9 ()-]*[0-9])[+0-9 ()-]{7,20}$",
				message = "must be a valid phone number") String phone,
		@Size(max = 100) String location,
		@Size(max = 100) String currentTitle,
		@Min(0) @Max(60) Integer experienceYears,
		@PositiveOrZero Long expectedSalary,
		@Min(0) @Max(365) Integer noticePeriodDays,
		@Size(max = 255) @URL String linkedinUrl,
		@Size(max = 255) @URL String githubUrl,
		@Size(max = 255) @URL String portfolioUrl,
		@Size(max = 20) Set<@NotBlank @Size(max = 100) String> targetRoles,
		@Size(max = 20) Set<@NotBlank @Size(max = 100) String> preferredLocations,
		@Size(max = 100) Set<@NotBlank @Size(max = 100) String> excludedCompanies,
		@Size(max = 100) Set<@NotBlank @Size(max = 100) String> excludedKeywords,
		@NotNull Boolean remoteOk,
		@NotNull @Min(0) @Max(100) Integer minMatchScore,
		@NotNull @Min(1) @Max(50) Integer dailyApplyLimit,
		@NotNull Boolean autoApplyEnabled,
		@Valid ApplicationDetails details,
		@Valid JobFilters filters) {

	/** Without job filters (older clients): the stored ones are kept. */
	public UpdateProfileRequest(String fullName, String phone, String location, String currentTitle, Integer experienceYears,
			Long expectedSalary, Integer noticePeriodDays, String linkedinUrl, String githubUrl, String portfolioUrl,
			Set<String> targetRoles, Set<String> preferredLocations, Set<String> excludedCompanies, Set<String> excludedKeywords,
			Boolean remoteOk, Integer minMatchScore, Integer dailyApplyLimit, Boolean autoApplyEnabled, ApplicationDetails details) {
		this(fullName, phone, location, currentTitle, experienceYears, expectedSalary, noticePeriodDays, linkedinUrl, githubUrl,
				portfolioUrl, targetRoles, preferredLocations, excludedCompanies, excludedKeywords, remoteOk, minMatchScore,
				dailyApplyLimit, autoApplyEnabled, details, null);
	}

}
