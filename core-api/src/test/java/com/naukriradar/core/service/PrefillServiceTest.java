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
