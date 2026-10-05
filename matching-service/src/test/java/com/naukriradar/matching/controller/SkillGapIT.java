package com.naukriradar.matching.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The skill-gap report against WireMock core-api and job-service. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SkillGapIT {

	private static final String USER_HEADER = "X-User-Id";

	@RegisterExtension
	static WireMockExtension services = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@DynamicPropertySource
	static void pointAtWireMock(DynamicPropertyRegistry registry) {
		registry.add("naukriradar.services.core-api-url", services::baseUrl);
		registry.add("naukriradar.services.job-service-url", services::baseUrl);
	}

	@Autowired
	private MockMvc mvc;

	@Test
	void theSkillThatUnlocksTheMostJobsComesFirstAndOwnedSkillsNeverShowUp() throws Exception {
		String user = UUID.randomUUID().toString();
		// at 85 the jobs miss by a skill or two: Docker lifts two of them over, Kubernetes one
		profile(user, "[\"java\", \"spring\"]", 85);
		services.stubFor(WireMock.post(urlEqualTo("/internal/v1/jobs/candidates")).willReturn(okJson("[%s, %s, %s, %s]".formatted(
				job("Backend Engineer", "[\"Java\", \"Spring Boot\", \"Docker\", \"Kubernetes\"]"),
				job("Backend Engineer", "[\"Java\", \"Docker\", \"AWS\"]"),
				job("Backend Engineer", "[\"Java\", \"Docker\"]"),
				job("Backend Engineer", "[]")))));

		String report = mvc.perform(MockMvcRequestBuilders.get("/api/v1/me/skill-gap").header(USER_HEADER, user))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

		assertThat(JsonPath.<Integer>read(report, "$.jobsAnalysed")).isEqualTo(4);
		assertThat(JsonPath.<String>read(report, "$.gaps[0].skill")).isEqualTo("docker");
		// the fourth job wasn't parsed: its description naming Docker counts too
		assertThat(JsonPath.<Integer>read(report, "$.gaps[0].jobsAsking")).isEqualTo(4);
		assertThat(JsonPath.<Integer>read(report, "$.gaps[0].extraMatches")).isPositive();
		assertThat(JsonPath.<List<String>>read(report, "$.gaps[0].exampleJobs")).hasSize(3);
		List<String> skills = JsonPath.read(report, "$.gaps[*].skill");
		assertThat(skills).contains("kubernetes", "aws").doesNotContain("java", "spring boot");
	}

	@Test
	void anEmptyProfileIsToldWhatToAddAndADownServiceIsA503() throws Exception {
		String empty = UUID.randomUUID().toString();
		services.stubFor(WireMock.get(urlEqualTo("/internal/v1/users/" + empty + "/matching-profile")).willReturn(okJson("""
				{"userId": "%s", "skills": [], "targetRoles": [], "remoteOk": false, "minMatchScore": 50}""".formatted(empty))));
		mvc.perform(MockMvcRequestBuilders.get("/api/v1/me/skill-gap").header(USER_HEADER, empty))
				.andExpect(status().isUnprocessableContent());

		String down = UUID.randomUUID().toString();
		services.stubFor(WireMock.get(urlEqualTo("/internal/v1/users/" + down + "/matching-profile"))
				.willReturn(aResponse().withStatus(503)));
		mvc.perform(MockMvcRequestBuilders.get("/api/v1/me/skill-gap").header(USER_HEADER, down))
				.andExpect(status().isServiceUnavailable());
	}

	private static void profile(String user, String skills, int minScore) {
		services.stubFor(WireMock.get(urlEqualTo("/internal/v1/users/" + user + "/matching-profile")).willReturn(okJson("""
				{"userId": "%s", "skills": %s, "targetRoles": ["Backend Engineer"], "preferredLocations": ["Pune"],
				 "remoteOk": false, "experienceYears": 4, "excludedCompanies": [], "excludedKeywords": [], "minMatchScore": %d}
				""".formatted(user, skills, minScore))));
	}

	private static String job(String title, String requiredSkills) {
		String id = UUID.randomUUID().toString();
		return """
				{"id": "%s", "title": "%s", "company": "Acme", "location": "Pune", "remote": false,
				 "postedAt": "%s", "applyUrl": "https://jobs.example.com/%s",
				 "description": "Services in Java. We ship with Docker.", "requiredSkills": %s, "minYearsExperience": 3}
				""".formatted(id, title, Instant.now().minus(1, ChronoUnit.HOURS), id, requiredSkills);
	}

}
