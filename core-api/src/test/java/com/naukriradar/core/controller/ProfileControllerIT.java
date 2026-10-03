package com.naukriradar.core.controller;

import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full stack: HTTP, validation, service, JPA and MySQL ({@code naukriradar_test}). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileControllerIT {

	private static final String USER_HEADER = "X-User-Id";

	private static final String VALID_PROFILE = """
			{
			  "fullName": "Satya",
			  "phone": "+91 98765 43210",
			  "location": "Lucknow",
			  "currentTitle": "Java Developer",
			  "experienceYears": 2,
			  "expectedSalary": 900000,
			  "noticePeriodDays": 30,
			  "linkedinUrl": "https://linkedin.com/in/satya",
			  "targetRoles": ["Backend Engineer", " backend engineer "],
			  "preferredLocations": ["Bengaluru", "Remote"],
			  "excludedCompanies": ["ACME Corp"],
			  "remoteOk": true,
			  "minMatchScore": 60,
			  "dailyApplyLimit": 15,
			  "autoApplyEnabled": false
			}
			""";

	@Autowired
	private MockMvc mvc;

	@Test
	void newUserGetsAnEmptyProfileWithSafeDefaults() throws Exception {
		String userId = createUser();

		mvc.perform(get("/api/v1/me/profile").header(USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(userId))
				.andExpect(jsonPath("$.autoApplyEnabled").value(false))
				.andExpect(jsonPath("$.dailyApplyLimit").value(10))
				.andExpect(jsonPath("$.minMatchScore").value(50))
				.andExpect(jsonPath("$.targetRoles").isEmpty());
	}

	@Test
	void duplicateEmailIsRejectedIgnoringCase() throws Exception {
		String email = uniqueEmail();
		createUser(email);

		mvc.perform(post("/api/v1/dev/users").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"" + email.toUpperCase() + "\"}"))
				.andExpect(status().isConflict())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
	}

	@Test
	void updatedProfileIsStoredAndNormalised() throws Exception {
		String userId = createUser();

		mvc.perform(put("/api/v1/me/profile").header(USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON).content(VALID_PROFILE))
				.andExpect(status().isOk());

		mvc.perform(get("/api/v1/me/profile").header(USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.fullName").value("Satya"))
				.andExpect(jsonPath("$.dailyApplyLimit").value(15))
				.andExpect(jsonPath("$.targetRoles", contains("Backend Engineer")))
				.andExpect(jsonPath("$.excludedCompanies", contains("acme corp")))
				.andExpect(jsonPath("$.preferredLocations", contains("Bengaluru", "Remote")));
	}

	@Test
	void invalidProfileReturnsProblemDetailsWithFieldErrors() throws Exception {
		String userId = createUser();
		String invalid = VALID_PROFILE
				.replace("\"dailyApplyLimit\": 15", "\"dailyApplyLimit\": 0")
				.replace("https://linkedin.com/in/satya", "not a url");

		mvc.perform(put("/api/v1/me/profile").header(USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON).content(invalid))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.title").value("Validation failed"))
				.andExpect(jsonPath("$.errors.dailyApplyLimit").exists())
				.andExpect(jsonPath("$.errors.linkedinUrl").exists());
	}

	@Test
	void autoApplyWithoutSkillsIsRejectedAsBusinessRule() throws Exception {
		String userId = createUser();
		String autoApply = VALID_PROFILE.replace("\"autoApplyEnabled\": false", "\"autoApplyEnabled\": true");

		mvc.perform(put("/api/v1/me/profile").header(USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON).content(autoApply))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("skills")));
	}

	@Test
	void skillsAreReplacedAndNormalised() throws Exception {
		String userId = createUser();

		mvc.perform(put("/api/v1/me/skills").header(USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"skills": [{"name": " Java ", "years": 2}, {"name": "JAVA", "years": 4}, {"name": "MySQL"}]}
						"""))
				.andExpect(status().isOk());

		mvc.perform(get("/api/v1/me/skills").header(USER_HEADER, userId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].name", contains("java", "mysql")))
				.andExpect(jsonPath("$[0].years").value(4))
				.andExpect(jsonPath("$[0].source").value("MANUAL"));
	}

	@Test
	void missingOrMalformedUserHeaderReturns401() throws Exception {
		mvc.perform(get("/api/v1/me/profile"))
				.andExpect(status().isUnauthorized())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

		mvc.perform(get("/api/v1/me/profile").header(USER_HEADER, "not-a-uuid"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void unknownUserReturns404() throws Exception {
		mvc.perform(get("/api/v1/me/profile").header(USER_HEADER, UUID.randomUUID().toString()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.status").value(404));
	}

	@Test
	void internalMatchingProfileHasOnlyWhatMatchingNeeds() throws Exception {
		String userId = createUser();
		mvc.perform(put("/api/v1/me/profile").header(USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON).content(VALID_PROFILE))
				.andExpect(status().isOk());
		mvc.perform(put("/api/v1/me/skills").header(USER_HEADER, userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"skills\": [{\"name\": \"Kafka\"}, {\"name\": \"Java\", \"years\": 3}]}"))
				.andExpect(status().isOk());

		mvc.perform(get("/internal/v1/users/" + userId + "/matching-profile"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(userId))
				.andExpect(jsonPath("$.skills", contains("java", "kafka")))
				.andExpect(jsonPath("$.targetRoles", contains("Backend Engineer")))
				.andExpect(jsonPath("$.excludedCompanies", contains("acme corp")))
				.andExpect(jsonPath("$.expectedSalary").value(900000))
				.andExpect(jsonPath("$.minMatchScore").value(60))
				.andExpect(jsonPath("$.phone").doesNotExist())
				.andExpect(jsonPath("$.fullName").doesNotExist());

		mvc.perform(get("/internal/v1/users/" + UUID.randomUUID() + "/matching-profile"))
				.andExpect(status().isNotFound());
	}

	private String createUser() throws Exception {
		return createUser(uniqueEmail());
	}

	private String createUser(String email) throws Exception {
		String body = mvc.perform(post("/api/v1/dev/users").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"" + email + "\"}"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(body, "$.id");
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}

}
