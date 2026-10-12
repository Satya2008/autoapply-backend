package com.naukriradar.core.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.naukriradar.core.model.Profile;
import com.naukriradar.core.repository.ResumeRepository;
import com.naukriradar.core.util.IndianNumberFormat;
import org.springframework.stereotype.Service;

/**
 * Turns a profile into ready answers for an application form, so applying by hand on a
 * risky site is copy, paste, submit. Fields the user hasn't filled in are left out rather
 * than sent blank.
 */
@Service
public class PrefillService {

	static final int MAX_SKILLS_LISTED = 15;

	private final ResumeRepository resumeRepository;

	public PrefillService(ResumeRepository resumeRepository) {
		this.resumeRepository = resumeRepository;
	}

	/** Must run inside a transaction: it reads the profile's user and collections. */
	public Map<String, String> answers(Profile profile) {
		Map<String, String> answers = new LinkedHashMap<>();
		put(answers, "fullName", profile.getFullName());
		// many forms ask for the two halves separately; everything after the first word is the last name
		if (profile.getFullName() != null && !profile.getFullName().isBlank()) {
			String[] parts = profile.getFullName().strip().split("\\s+", 2);
			put(answers, "firstName", parts[0]);
			put(answers, "lastName", parts.length > 1 ? parts[1] : null);
		}
		put(answers, "email", profile.getUser().getEmail());
		put(answers, "phone", profile.getPhone());
		// forms with their own country-code box want the number alone: +91 96166 92093 -> 9616692093
		put(answers, "phoneNational", nationalNumber(profile.getPhone()));
		// the country a form pairs with the phone number; an Indian number means India
		if (profile.getPhone() != null && profile.getPhone().replaceAll("[^0-9+]", "").startsWith("+91")) {
			put(answers, "country", "India");
		}
		put(answers, "location", profile.getLocation());
		put(answers, "currentTitle", profile.getCurrentTitle());
		if (profile.getExperienceYears() != null) {
			int years = profile.getExperienceYears();
			put(answers, "totalExperience", years == 1 ? "1 year" : years + " years");
		}
		if (profile.getNoticePeriodDays() != null) {
			int days = profile.getNoticePeriodDays();
			put(answers, "noticePeriod", days == 0 ? "Immediate" : days + " days");
		}
		if (profile.getExperienceYears() != null) {
			put(answers, "totalExperienceYears", Integer.toString(profile.getExperienceYears()));
		}
		if (profile.getNoticePeriodDays() != null) {
			put(answers, "noticePeriodDays", Integer.toString(profile.getNoticePeriodDays()));
		}
		if (profile.getExpectedSalary() != null) {
			put(answers, "expectedSalary", "₹" + IndianNumberFormat.format(profile.getExpectedSalary()) + " per year");
			put(answers, "expectedCtcLakhs", lakhs(profile.getExpectedSalary()));
		}
		put(answers, "currentCompany", profile.getCurrentCompany());
		if (profile.getCurrentCtc() != null) {
			put(answers, "currentCtc", "₹" + IndianNumberFormat.format(profile.getCurrentCtc()) + " per year");
			put(answers, "currentCtcLakhs", lakhs(profile.getCurrentCtc()));
		}
		put(answers, "highestDegree", profile.getHighestDegree());
		put(answers, "fieldOfStudy", profile.getFieldOfStudy());
		put(answers, "college", profile.getCollege());
		if (profile.getEducationStartYear() != null) {
			put(answers, "educationStartYear", Integer.toString(profile.getEducationStartYear()));
		}
		if (profile.getGraduationYear() != null) {
			put(answers, "graduationYear", Integer.toString(profile.getGraduationYear()));
		}
		if (profile.getWillingToRelocate() != null) {
			put(answers, "willingToRelocate", profile.getWillingToRelocate() ? "Yes" : "No");
		}
		// the questions most forms add: answered once in the profile, filled everywhere
		put(answers, "gender", profile.getGender());
		put(answers, "heardAbout", profile.getHeardAbout());
		if (profile.getAuthorizedToWork() != null) {
			put(answers, "authorizedToWork", profile.getAuthorizedToWork() ? "Yes" : "No");
		}
		if (profile.getNeedsSponsorship() != null) {
			put(answers, "needsSponsorship", profile.getNeedsSponsorship() ? "Yes" : "No");
		}
		put(answers, "disability", profile.getDisability());
		put(answers, "veteranStatus", profile.getVeteranStatus());
		put(answers, "linkedinUrl", profile.getLinkedinUrl());
		put(answers, "githubUrl", profile.getGithubUrl());
		put(answers, "portfolioUrl", profile.getPortfolioUrl());
		if (!profile.getSkills().isEmpty()) {
			put(answers, "skills", profile.getSkills().keySet().stream().sorted().limit(MAX_SKILLS_LISTED)
					.collect(Collectors.joining(", ")));
		}
		resumeRepository.findByUserId(profile.getUserId())
				.ifPresent(resume -> put(answers, "resume", resume.getFileName() + " (download from /api/v1/me/resume/file)"));
		return answers;
	}

	/** Indian numbers without the country code; other numbers as typed. */
	static String nationalNumber(String phone) {
		if (phone == null) {
			return null;
		}
		String digits = phone.replaceAll("[^0-9]", "");
		if (digits.length() == 12 && digits.startsWith("91")) {
			return digits.substring(2);
		}
		if (digits.length() == 11 && digits.startsWith("0")) {
			return digits.substring(1);
		}
		return digits.isEmpty() ? null : digits;
	}

	/** 1450000 -> "14.5" (forms ask CTC in lakhs). */
	static String lakhs(long rupees) {
		BigDecimal value = BigDecimal.valueOf(rupees).divide(BigDecimal.valueOf(100_000), 2, RoundingMode.HALF_UP);
		return value.stripTrailingZeros().toPlainString();
	}

	private static void put(Map<String, String> answers, String key, String value) {
		if (value != null && !value.isBlank()) {
			answers.put(key, value.strip());
		}
	}

}
