package com.naukriradar.core.user;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.naukriradar.common.web.BusinessRuleException;
import com.naukriradar.common.web.NotFoundException;
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

	private static final UUID USER_ID = UUID.randomUUID();

	@Mock
	private ProfileRepository profiles;

	private ProfileService service;

	private Profile profile;

	@BeforeEach
	void setUp() {
		service = new ProfileService(profiles, new ProfileMapper());
		profile = new Profile(new User("candidate@example.com"));
	}

	@Test
	void updateProfileTrimsAndDeduplicatesTargetRolesIgnoringCase() {
		givenProfileExists();
		givenSaveReturnsArgument();

		ProfileResponse response = service.updateProfile(USER_ID,
				request(Set.of("  Backend   Engineer ", "backend engineer", "Java Developer"), Set.of(), false));

		assertThat(response.targetRoles()).hasSize(2)
				.anyMatch(role -> role.equalsIgnoreCase("backend engineer"))
				.contains("Java Developer")
				.allMatch(role -> role.equals(role.trim()) && !role.contains("  "));
	}

	@Test
	void updateProfileStoresExcludedCompaniesInLowerCase() {
		givenProfileExists();
		givenSaveReturnsArgument();

		ProfileResponse response = service.updateProfile(USER_ID, request(Set.of(), Set.of(" ACME Corp ", "acme corp"), false));

		assertThat(response.excludedCompanies()).containsExactly("acme corp");
	}

	@Test
	void updateProfileRejectsAutoApplyWithoutTargetRoles() {
		givenProfileExists();

		assertThatThrownBy(() -> service.updateProfile(USER_ID, request(Set.of(), Set.of(), true)))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("target role");
	}

	@Test
	void updateProfileRejectsAutoApplyWithTooFewSkills() {
		givenProfileExists();
		profile.getSkills().put("java", new ProfileSkill(3, SkillSource.MANUAL));

		assertThatThrownBy(() -> service.updateProfile(USER_ID, request(Set.of("Backend Engineer"), Set.of(), true)))
				.isInstanceOf(BusinessRuleException.class)
				.hasMessageContaining("skills");
	}

	@Test
	void updateProfileAllowsAutoApplyOncePrerequisitesAreMet() {
		givenProfileExists();
		givenSaveReturnsArgument();
		givenSkills("java", "spring", "mysql");

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
		givenSkills("java", "spring", "mysql");
		profile.getTargetRoles().add("Backend Engineer");
		profile.setAutoApplyEnabled(true);

		assertThatThrownBy(() -> service.replaceSkills(USER_ID,
				new ReplaceSkillsRequest(List.of(new SkillRequest("java", 3)))))
				.isInstanceOf(BusinessRuleException.class);
	}

	@Test
	void getProfileThrowsNotFoundForUnknownUser() {
		when(profiles.findById(USER_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.getProfile(USER_ID)).isInstanceOf(NotFoundException.class);
	}

	private void givenProfileExists() {
		when(profiles.findById(USER_ID)).thenReturn(Optional.of(profile));
	}

	private void givenSaveReturnsArgument() {
		when(profiles.saveAndFlush(any(Profile.class))).thenAnswer(invocation -> invocation.getArgument(0));
	}

	private void givenSkills(String... names) {
		for (String name : names) {
			profile.getSkills().put(name, new ProfileSkill(2, SkillSource.MANUAL));
		}
	}

	private static UpdateProfileRequest request(Set<String> targetRoles, Set<String> excludedCompanies,
			boolean autoApply) {
		return new UpdateProfileRequest("Satya", null, "Lucknow", "Java Developer", 2, 600_000L, 30,
				null, null, null, targetRoles, Set.of(), excludedCompanies, Set.of(), true, 60, 10, autoApply);
	}

}
