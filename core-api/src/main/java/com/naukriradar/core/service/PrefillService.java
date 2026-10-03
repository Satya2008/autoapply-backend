package com.naukriradar.core.service;

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
		put(answers, "email", profile.getUser().getEmail());
		put(answers, "phone", profile.getPhone());
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
		if (profile.getExpectedSalary() != null) {
			put(answers, "expectedSalary", "₹" + IndianNumberFormat.format(profile.getExpectedSalary()) + " per year");
		}
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

	private static void put(Map<String, String> answers, String key, String value) {
		if (value != null && !value.isBlank()) {
			answers.put(key, value.strip());
		}
	}

}
