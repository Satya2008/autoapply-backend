package com.naukriradar.matching.ai;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import com.naukriradar.matching.dto.request.AiProviderCreateRequest;
import com.naukriradar.matching.dto.request.AiProviderUpdateRequest;
import com.naukriradar.matching.model.AiProviderType;
import com.naukriradar.matching.repository.AiProviderRepository;
import com.naukriradar.matching.service.AiProviderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Matching with AI on top: parsed requirements, the AI review of top matches, the internal AI API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiMatchingIT {

	private static final String USER_HEADER = "X-User-Id";

	private static final String JOB_FIT = """
			{"content": [{"type": "text", "text": "{\\"score\\": 88, \\"reasons\\": [\\"Strong Java\\", \\"Right city\\"]}"}],
			 "usage": {"input_tokens": 400, "output_tokens": 30}}""";

	@RegisterExtension
	static WireMockExtension services = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@DynamicPropertySource
	static void pointAtWireMock(DynamicPropertyRegistry registry) {
		registry.add("naukriradar.services.core-api-url", services::baseUrl);
		registry.add("naukriradar.services.job-service-url", services::baseUrl);
	}

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AiProviderService providers;

	@Autowired
	private AiProviderRepository providerRepository;

	@BeforeEach
	void fakeAi() {
		services.stubFor(WireMock.post(urlPathEqualTo("/ai/v1/messages")).willReturn(okJson(JOB_FIT)));
		if (!providerRepository.existsByName("fit-ai")) {
			providers.create(new AiProviderCreateRequest("fit-ai", AiProviderType.ANTHROPIC, services.baseUrl() + "/ai",
					"key-123456789", "model-a", null, true, null, null, null), "test");
		}
		aiOn(true);
	}

	@Test
	void theTopMatchesGetAnAiReviewAndParsedSkillsDriveTheLocalScore() throws Exception {
		String user = UUID.randomUUID().toString();
		stubProfile(user);
		stubCandidates(parsedJob("ai-1"));

		runToEnd(user)
				.andExpect(jsonPath("$.status").value("SUCCESS"))
				.andExpect(jsonPath("$.aiReviewed").value(1))
				.andExpect(jsonPath("$.message", containsString("AI reviewed the top 1")));

		String page = mvc.perform(get("/api/v1/me/matches", user)).andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<Integer>read(page, "$.items[0].aiScore")).isEqualTo(88);
		String matchId = JsonPath.read(page, "$.items[0].id");
		String detail = mvc.perform(get("/api/v1/me/matches/" + matchId, user)).andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<String>>read(detail, "$.aiReasons")).containsExactly("Strong Java", "Right city");
		assertThat(JsonPath.<String>read(detail, "$.aiScoredBy")).isEqualTo("fit-ai:model-a");
		List<String> skillReason = JsonPath.read(detail, "$.breakdown[?(@.factor == 'skills')].detail");
		assertThat(skillReason).containsExactly("Has 1 of the 2 skills the job asks for; missing Kafka.");

		// the same profile and job again: answered from the cache, not paid for twice
		int calls = services.findAll(postRequestedFor(urlPathEqualTo("/ai/v1/messages"))).size();
		runToEnd(user).andExpect(jsonPath("$.aiReviewed").value(1));
		assertThat(services.findAll(postRequestedFor(urlPathEqualTo("/ai/v1/messages")))).hasSize(calls);
	}

	@Test
	void withoutAiTheRunStillSucceedsOnLocalScores() throws Exception {
		aiOn(false);
		String user = UUID.randomUUID().toString();
		stubProfile(user);
		stubCandidates(parsedJob("ai-2"));

		runToEnd(user)
				.andExpect(jsonPath("$.status").value("SUCCESS"))
				.andExpect(jsonPath("$.aiReviewed").value(0))
				.andExpect(jsonPath("$.message", containsString("AI review skipped")));
		mvc.perform(get("/api/v1/me/matches", user)).andExpect(jsonPath("$.items[0].aiScore").value(nullValue()));
	}

	@Test
	void otherServicesGetAnAnswerOrAReasonNeverAnError() throws Exception {
		String body = "{\"prompt\": \"job-fit\", \"variables\": {\"profile\": \"Java dev\", \"job\": \"Java role "
				+ UUID.randomUUID() + "\"}}";
		mvc.perform(MockMvcRequestBuilders.post("/internal/v1/ai/run").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer.score").value(88))
				.andExpect(jsonPath("$.provider").value("fit-ai"));

		aiOn(false);
		mvc.perform(MockMvcRequestBuilders.post("/internal/v1/ai/run").contentType(MediaType.APPLICATION_JSON)
				.content(body.replace("Java role", "Other role")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.answer").value(nullValue()))
				.andExpect(jsonPath("$.unavailableReason", containsString("No AI provider is ready")));
		mvc.perform(MockMvcRequestBuilders.post("/internal/v1/ai/run").contentType(MediaType.APPLICATION_JSON)
				.content("{\"prompt\": \"no-such-prompt\"}"))
				.andExpect(status().isNotFound());
	}

	private void aiOn(boolean on) {
		providers.list().forEach(p -> providers.update(p.name(),
				new AiProviderUpdateRequest(null, null, null, null, on && p.name().equals("fit-ai"), null, null, null), "test"));
	}

	private ResultActions runToEnd(String user) throws Exception {
		String runId = JsonPath.read(mvc.perform(MockMvcRequestBuilders.post("/api/v1/me/matches/runs").header(USER_HEADER, user))
				.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.id");
		Instant deadline = Instant.now().plus(20, ChronoUnit.SECONDS);
		while (Instant.now().isBefore(deadline)) {
			ResultActions result = mvc.perform(get("/api/v1/me/matches/runs/" + runId, user));
			if (!"RUNNING".equals(JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.status"))) {
				return result;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Run did not finish");
	}

	private static MockHttpServletRequestBuilder get(String path, String user) {
		return MockMvcRequestBuilders.get(path).header(USER_HEADER, user);
	}

	private static void stubProfile(String user) {
		services.stubFor(WireMock.get(urlEqualTo("/internal/v1/users/" + user + "/matching-profile")).willReturn(okJson("""
				{"userId": "%s", "skills": ["java", "mysql"], "targetRoles": ["Backend Engineer"],
				 "preferredLocations": ["Pune"], "remoteOk": false, "experienceYears": 5,
				 "excludedCompanies": [], "excludedKeywords": [], "minMatchScore": 40}
				""".formatted(user))));
	}

	private static void stubCandidates(String job) {
		services.stubFor(WireMock.post(urlEqualTo("/internal/v1/jobs/candidates")).willReturn(okJson("[" + job + "]")));
	}

	/** A job job-service has already parsed: the requirements come with it. */
	private static String parsedJob(String id) {
		return """
				{"id": "%s", "title": "Backend Engineer", "company": "Acme", "location": "Pune", "remote": false,
				 "postedAt": "%s", "applyUrl": "https://jobs.example.com/%s",
				 "description": "We build services in Java and Kafka. Team %s.",
				 "requiredSkills": ["Java", "Kafka"], "minYearsExperience": 4, "seniority": "senior"}
				""".formatted(id, Instant.now().minus(1, ChronoUnit.HOURS), id, UUID.randomUUID());
	}

}
