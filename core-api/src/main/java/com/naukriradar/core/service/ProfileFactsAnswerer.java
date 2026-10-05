package com.naukriradar.core.service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import com.naukriradar.core.dto.response.ProfileResponse;
import com.naukriradar.core.dto.response.SkillResponse;
import com.naukriradar.core.util.IndianNumberFormat;
import org.springframework.stereotype.Component;

/**
 * Answers the screening questions a profile field answers exactly: notice period, years of
 * experience (overall or in one skill), location, expected salary, current title, profile
 * links. Exact and free, so these never go to AI. Anything it can't answer for sure, it leaves.
 */
@Component
public class ProfileFactsAnswerer {

	private static final Pattern NOTICE = Pattern.compile("notice period|how soon can you join|when can you (join|start)|joining time");

	private static final Pattern YEARS = Pattern.compile("how many years|years of (\\w+ )?experience|experience in years|total experience");

	private static final Pattern LOCATION = Pattern.compile("current (location|city)|where are you (located|based)|which city");

	private static final Pattern SALARY = Pattern.compile("expected (ctc|salary|compensation)|salary expectation|expected package");

	private static final Pattern TITLE = Pattern.compile("current (job )?(title|role|designation)");

	public Optional<String> answer(String question, ProfileResponse profile, List<SkillResponse> skills) {
		String q = question.toLowerCase(Locale.ROOT);
		if (NOTICE.matcher(q).find() && profile.noticePeriodDays() != null) {
			int days = profile.noticePeriodDays();
			return Optional.of(days == 0 ? "I can join immediately." : days + " days.");
		}
		if (YEARS.matcher(q).find()) {
			// "years of experience in Java": that skill's years when we know them, else nothing (the total would mislead)
			for (SkillResponse skill : skills) {
				if (mentions(q, skill.name())) {
					return skill.years() == null ? Optional.empty()
							: Optional.of(skill.years() + (skill.years() == 1 ? " year." : " years."));
				}
			}
			if (profile.experienceYears() != null) {
				return Optional.of(profile.experienceYears() + (profile.experienceYears() == 1 ? " year." : " years."));
			}
		}
		if (LOCATION.matcher(q).find() && notBlank(profile.location())) {
			return Optional.of(profile.location().strip() + ".");
		}
		if (SALARY.matcher(q).find() && profile.expectedSalary() != null) {
			return Optional.of("INR " + IndianNumberFormat.format(profile.expectedSalary()) + " per year.");
		}
		if (TITLE.matcher(q).find() && notBlank(profile.currentTitle())) {
			return Optional.of(profile.currentTitle().strip() + ".");
		}
		if (q.contains("linkedin") && notBlank(profile.linkedinUrl())) {
			return Optional.of(profile.linkedinUrl());
		}
		if (q.contains("github") && notBlank(profile.githubUrl())) {
			return Optional.of(profile.githubUrl());
		}
		if (q.contains("portfolio") && notBlank(profile.portfolioUrl())) {
			return Optional.of(profile.portfolioUrl());
		}
		return Optional.empty();
	}

	private static boolean mentions(String lowerQuestion, String skill) {
		String s = skill.toLowerCase(Locale.ROOT);
		return Pattern.compile("(?<![\\p{L}\\p{N}])" + Pattern.quote(s) + "(?![\\p{L}\\p{N}+#])").matcher(lowerQuestion).find();
	}

	private static boolean notBlank(String value) {
		return value != null && !value.isBlank();
	}

}
