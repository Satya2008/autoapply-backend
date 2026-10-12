package com.naukriradar.core.service;

import java.util.Map;
import java.util.Optional;

import com.naukriradar.core.model.Profile;
import com.naukriradar.core.model.ProfileSkill;
import com.naukriradar.core.model.SkillSource;
import com.naukriradar.core.model.User;
import com.naukriradar.core.repository.ResumeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrefillServiceTest {

	@Mock
	private ResumeRepository resumeRepository;

	@Test
	void givesFormsTheShapesTheyAskFor() {
		when(resumeRepository.findByUserId(any())).thenReturn(Optional.empty());
		Profile profile = new Profile(new User("satya@example.com"));
		profile.setFullName("Satyanand Kumar Shrivastava");
		profile.setPhone("+91 96166 92093");
		profile.setCurrentCtc(1_450_000L);
		profile.setExpectedSalary(2_000_000L);
		profile.setGraduationYear(2015);
		profile.setWillingToRelocate(true);
		profile.setGender("Male");
		profile.setHeardAbout("LinkedIn");
		profile.setAuthorizedToWork(true);
		profile.setNeedsSponsorship(false);

		assertThat(new PrefillService(resumeRepository).answers(profile))
				.containsEntry("firstName", "Satyanand")
				.containsEntry("lastName", "Kumar Shrivastava")
				// a form with its own +91 box must not get the code twice
				.containsEntry("phoneNational", "9616692093")
				.containsEntry("country", "India")
				.containsEntry("currentCtcLakhs", "14.5")
				.containsEntry("expectedCtcLakhs", "20")
				.containsEntry("graduationYear", "2015")
				.containsEntry("willingToRelocate", "Yes")
				.containsEntry("gender", "Male")
				.containsEntry("heardAbout", "LinkedIn")
				.containsEntry("authorizedToWork", "Yes")
				.containsEntry("needsSponsorship", "No")
				.doesNotContainKey("disability");
	}

	@Test
	void fillsWhatTheProfileHasInAFormFriendlyWay() {
		when(resumeRepository.findByUserId(any())).thenReturn(Optional.empty());
		Profile profile = new Profile(new User("satya@example.com"));
		profile.setFullName("Satya");
		profile.setPhone("+91 98765 43210");
		profile.setExperienceYears(3);
		profile.setNoticePeriodDays(0);
		profile.setExpectedSalary(1_200_000L);
		profile.setLinkedinUrl("https://linkedin.com/in/satya");
		profile.getSkills().put("spring boot", new ProfileSkill(2, SkillSource.MANUAL));
		profile.getSkills().put("java", new ProfileSkill(3, SkillSource.RESUME));

		Map<String, String> answers = new PrefillService(resumeRepository).answers(profile);

		assertThat(answers)
				.containsEntry("fullName", "Satya")
				.containsEntry("email", "satya@example.com")
				.containsEntry("totalExperience", "3 years")
				.containsEntry("noticePeriod", "Immediate")
				.containsEntry("expectedSalary", "₹12,00,000 per year")
				.containsEntry("skills", "java, spring boot")
				.doesNotContainKeys("githubUrl", "portfolioUrl", "location", "resume");
	}

	@Test
	void emptyProfileStillGivesTheEmail() {
		when(resumeRepository.findByUserId(any())).thenReturn(Optional.empty());

		Map<String, String> answers = new PrefillService(resumeRepository).answers(new Profile(new User("a@b.co")));

		assertThat(answers).containsOnlyKeys("email");
	}

}
