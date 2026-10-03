package com.naukriradar.core.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.naukriradar.common.exception.BusinessRuleException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.dto.request.ReplaceSkillsRequest;
import com.naukriradar.core.dto.request.SkillRequest;
import com.naukriradar.core.dto.request.UpdateProfileRequest;
import com.naukriradar.core.dto.response.ProfileResponse;
import com.naukriradar.core.dto.response.SkillResponse;
import com.naukriradar.core.mapper.ProfileMapper;
import com.naukriradar.core.model.Profile;
import com.naukriradar.core.model.ProfileSkill;
import com.naukriradar.core.model.SkillSource;
import com.naukriradar.core.model.User;
import com.naukriradar.core.repository.ProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

	private static final String USER_ID = UUID.randomUUID().toString();

	@Mock
	private ProfileRepository profileRepository;

	private ProfileService service;

	private Profile profile;

	@BeforeEach
	void setUp() {
		service = new ProfileService(profileRepository, new ProfileMapper());
		profile = new Profile(new User("candidate@example.com"));
	}

	@Test
	void trimsAndDeduplicatesTargetRolesIgnoringCase() {
		givenProfileExists();
		givenSaveReturnsArgument();

		ProfileResponse response = service.updateProfile(USER_ID,
				request(Set.of("  Backend   Engineer ", "backend engineer", "Java Developer"), Set.of(), false));

		assertThat(response.targetRoles()).hasSize(2)
				.anyMatch(role -> role.equalsIgnoreCase("backend engineer"))
				.contains("Java Developer")
				.allMatch(role -> role.equals(role.strip()) && !role.contains("  "));
	}

	@Test
	void storesExcludedCompaniesInLowerCase() {
		givenProfileExists();
		givenSaveReturnsArgument();

		ProfileResponse response = service.updateProfile(USER_ID, request(Set.of(), Set.of(" ACME Corp ", "acme corp"), false));

		assertThat(response.excludedCompanies()).containsExactly("acme corp");
	}

	@Test
	void blankTextFieldsAreStoredAsNull() {
		givenProfileExists();
		givenSaveReturnsArgument();
		UpdateProfileRequest blanks = new UpdateProfileRequest("   ", null, " ", null, null, null, null, "", null, null,
				Set.of(), Set.of(), Set.of(), Set.of(), false, 50, 10, false);

		ProfileResponse response = service.updateProfile(USER_ID, blanks);

		assertThat(response.fullName()).isNull();
		assertThat(response.location()).isNull();
		assertThat(response.linkedinUrl()).isNull();
	}

	@Test
	void rejectsAutoApplyWithoutTargetRoles() {
		givenProfileExists();

		assertThatThrownBy(() -> service.updateProfile(USER_ID, request(Set.of(), Set.of(), true)))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("target role");
	}

	@Test
	void rejectsAutoApplyWithTooFewSkills() {
		givenProfileExists();
		profile.getSkills().put("java", new ProfileSkill(3, SkillSource.MANUAL));

		assertThatThrownBy(() -> service.updateProfile(USER_ID, request(Set.of("Backend Engineer"), Set.of(), true)))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("skills");
	}

	@Test
	void allowsAutoApplyOncePrerequisitesAreMet() {
		givenProfileExists();
		givenSaveReturnsArgument();
		givenSkills(SkillSource.MANUAL, "java", "spring", "mysql");

		ProfileResponse response = service.updateProfile(USER_ID, request(Set.of("Backend Engineer"), Set.of(), true));

		assertThat(response.autoApplyEnabled()).isTrue();
	}

	@Test
	void replaceSkillsNormalisesNamesAndKeepsTheHigherExperience() {
		givenProfileExists();
		givenSaveReturnsArgument();

		List<SkillResponse> skills = service.replaceSkills(USER_ID, new ReplaceSkillsRequest(List.of(
				new SkillRequest(" Java ", 2),
				new SkillRequest("JAVA", 5),
				new SkillRequest("Spring  Boot", null))));

		assertThat(skills).containsExactly(
				new SkillResponse("java", 5, SkillSource.MANUAL),
				new SkillResponse("spring boot", null, SkillSource.MANUAL));
	}

	@Test
	void replaceSkillsRejectsDroppingBelowTheMinimumWhileAutoApplyIsOn() {
		givenProfileExists();
		givenSkills(SkillSource.MANUAL, "java", "spring", "mysql");
		profile.getTargetRoles().add("Backend Engineer");
		profile.setAutoApplyEnabled(true);

		assertThatThrownBy(() -> service.replaceSkills(USER_ID,
				new ReplaceSkillsRequest(List.of(new SkillRequest("java", 3)))))
				.isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void resumeSkillsNeverOverwriteManualOnes() {
		givenProfileExists();
		givenSaveReturnsArgument();
		profile.getSkills().put("java", new ProfileSkill(4, SkillSource.MANUAL));

		ProfileService.ResumeSkillsUpdate update = service.applyResumeSkills(USER_ID, List.of("java", "docker"));

		assertThat(update.added()).containsExactly("docker");
		assertThat(profile.getSkills().get("java").getSource()).isEqualTo(SkillSource.MANUAL);
		assertThat(profile.getSkills().get("java").getYears()).isEqualTo(4);
	}

	@Test
	void newResumeReplacesSkillsFromTheOldResume() {
		givenProfileExists();
		givenSaveReturnsArgument();
		givenSkills(SkillSource.RESUME, "angular", "java");
		givenSkills(SkillSource.MANUAL, "mysql");

		service.applyResumeSkills(USER_ID, List.of("java", "kafka"));

		assertThat(profile.getSkills()).containsOnlyKeys("java", "kafka", "mysql");
	}

	@Test
	void autoApplyIsSwitchedOffWhenANewResumeLeavesTooFewSkills() {
		givenProfileExists();
		givenSaveReturnsArgument();
		givenSkills(SkillSource.RESUME, "java", "spring", "mysql");
		profile.getTargetRoles().add("Backend Engineer");
		profile.setAutoApplyEnabled(true);

		ProfileService.ResumeSkillsUpdate update = service.applyResumeSkills(USER_ID, List.of("java"));

		assertThat(update.autoApplyTurnedOff()).isTrue();
		assertThat(profile.isAutoApplyEnabled()).isFalse();
	}

	@Test
	void resumeSkillsStopAtTheProfileLimit() {
		givenProfileExists();
		givenSaveReturnsArgument();
		for (int i = 0; i < ProfileService.MAX_SKILLS - 1; i++) {
			profile.getSkills().put("manual-" + i, new ProfileSkill(null, SkillSource.MANUAL));
		}

		ProfileService.ResumeSkillsUpdate update = service.applyResumeSkills(USER_ID, List.of("java", "kafka", "docker"));

		assertThat(update.added()).containsExactly("java");
		assertThat(profile.getSkills()).hasSize(ProfileService.MAX_SKILLS);
	}

	@Test
	void unknownUserIsNotFound() {
		when(profileRepository.findById(USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getProfile(USER_ID)).isInstanceOf(NotFoundException.class);
	}

	private void givenProfileExists() {
		when(profileRepository.findById(USER_ID)).thenReturn(Optional.of(profile));
	}

	private void givenSaveReturnsArgument() {
		when(profileRepository.saveAndFlush(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private void givenSkills(SkillSource source, String... names) {
		for (String name : names) {
			profile.getSkills().put(name, new ProfileSkill(2, source));
		}
	}

	private static UpdateProfileRequest request(Set<String> targetRoles, Set<String> excludedCompanies,
			boolean autoApply) {
		return new UpdateProfileRequest("Satya", null, "Lucknow", "Java Developer", 2, 600_000L, 30,
				null, null, null, targetRoles, Set.of(), excludedCompanies, Set.of(), true, 60, 10, autoApply);
	}

}
