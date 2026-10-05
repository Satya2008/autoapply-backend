package com.naukriradar.matching.controller;

import java.io.InputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The golden set, matcher and prompt evals, and the activation gate they guard. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EvalAdminIT {

	private static final String JUDGE = "eval-ai";

	@RegisterExtension
	static WireMockExtension vendor = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AiProviderService providers;

	@Autowired
	private AiProviderRepository providerRepository;

	@Autowired
	private JsonMapper json;

	@BeforeEach
	void aJudge() {
		if (!providerRepository.existsByName(JUDGE)) {
			providers.create(new AiProviderCreateRequest(JUDGE, AiProviderType.ANTHROPIC, vendor.baseUrl() + "/judge",
					"key-judge-123456", "model-a", null, true, null, null, null, null), "test");
		}
		only(JUDGE);
	}

	private void only(String name) {
		providers.list().forEach(p -> providers.update(p.name(),
				new AiProviderUpdateRequest(null, null, null, null, p.name().equals(name), null, null, null, null), "test"));
	}

	@Test
	void theBuiltInGoldenSetIsThereAndCasesCanBeManaged() throws Exception {
		mvc.perform(MockMvcRequestBuilders.get("/api/v1/admin/evals/cases"))
				.andExpect(jsonPath("$.length()", greaterThanOrEqualTo(16)))
				.andExpect(jsonPath("$[?(@.name == 'java-backend-vs-spring-microservices')].expectedScore").value(82));

		String name = "case-" + UUID.randomUUID().toString().substring(0, 8);
		String body = """
				{"name": "%s", "profile": {"skills": ["go"], "targetRoles": ["Go Developer"]},
				 "job": {"title": "Golang Engineer", "description": "Go services"}, "expectedScore": 85}""".formatted(name);
		String created = mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/evals/cases")
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/evals/cases").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict());
		mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/evals/cases").contentType(MediaType.APPLICATION_JSON)
				.content(body.replace("85", "140").replace(name, name + "x")))
				.andExpect(status().isBadRequest());
		mvc.perform(MockMvcRequestBuilders.delete("/api/v1/admin/evals/cases/" + JsonPath.read(created, "$.id")))
				.andExpect(status().isNoContent());
		mvc.perform(MockMvcRequestBuilders.delete("/api/v1/admin/evals/cases/nope")).andExpect(status().isNotFound());
	}

	@Test
	void theMatcherEvalPutsKeywordAndHybridSideBySide() throws Exception {
		String run = finished(start("{\"kind\": \"MATCHER\"}"));

		assertThat(JsonPath.<String>read(run, "$.status")).isEqualTo("SUCCEEDED");
		assertThat(JsonPath.<String>read(run, "$.metrics.embeddingModel")).isEqualTo("local:hashing-v1");
		assertThat(JsonPath.<Integer>read(run, "$.metrics.keyword.cases")).isGreaterThanOrEqualTo(16);
		assertThat(JsonPath.<Double>read(run, "$.metrics.hybrid.mae")).isNotNull();
		assertThat(JsonPath.<String>read(run, "$.metrics.verdict")).contains("keyword-only");
		mvc.perform(MockMvcRequestBuilders.get("/api/v1/admin/evals/runs")).andExpect(jsonPath("$[0].kind").exists());
	}

	@Test
	void aNewPromptVersionGoesLiveOnlyAfterItPassesAnEvalAndRollbackNeedsNone() throws Exception {
		stubJudgeWithTheExpectedScores();
		String added = mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/prompts/job-fit/versions")
				.contentType(MediaType.APPLICATION_JSON).content(jobFitVersion("v-" + UUID.randomUUID())))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		int version = JsonPath.read(added, "$.version");

		mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/prompts/job-fit/versions/" + version + "/activate"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.detail", containsString("has not passed an eval")));

		String run = finished(start("{\"kind\": \"PROMPT\", \"promptCode\": \"job-fit\", \"promptVersion\": " + version + "}"));
		assertThat(JsonPath.<String>read(run, "$.status")).isEqualTo("SUCCEEDED");
		assertThat(JsonPath.<Boolean>read(run, "$.passed")).isTrue();
		assertThat(JsonPath.<Double>read(run, "$.metrics.ai.mae")).isZero();
		assertThat(JsonPath.<Double>read(run, "$.metrics.validRate")).isEqualTo(1.0);

		mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/prompts/job-fit/versions/" + version + "/activate"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
		// back to the version that was live before: no eval needed
		mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/prompts/job-fit/versions/1/activate"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.evalGated").value(true));
	}

	@Test
	void aPromptEvalWithoutAiFailsAndSaysWhy() throws Exception {
		// its own provider, so the failures don't open the circuit of the judge other tests use
		if (!providerRepository.existsByName("judge-down")) {
			providers.create(new AiProviderCreateRequest("judge-down", AiProviderType.ANTHROPIC, vendor.baseUrl() + "/down",
					"key-down-123456", "model-a", null, true, null, null, null, null), "test");
		}
		vendor.stubFor(WireMock.post(urlPathEqualTo("/down/v1/messages")).willReturn(aResponse().withStatus(401)));
		only("judge-down");
		String added = mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/prompts/job-fit/versions")
				.contentType(MediaType.APPLICATION_JSON).content(jobFitVersion("down-" + UUID.randomUUID())))
				.andReturn().getResponse().getContentAsString();

		String run = finished(start("{\"kind\": \"PROMPT\", \"promptCode\": \"job-fit\", \"promptVersion\": "
				+ JsonPath.read(added, "$.version") + "}"));

		assertThat(JsonPath.<String>read(run, "$.status")).isEqualTo("FAILED");
		assertThat(JsonPath.<String>read(run, "$.error")).contains("no usable answer");
		mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/evals/runs").contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\": \"PROMPT\", \"promptCode\": \"cover-letter\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/evals/runs").contentType(MediaType.APPLICATION_JSON)
				.content("{\"kind\": \"PROMPT\", \"promptCode\": \"job-fit\", \"promptVersion\": 999}"))
				.andExpect(status().isNotFound());
		mvc.perform(MockMvcRequestBuilders.get("/api/v1/admin/evals/runs/nope")).andExpect(status().isNotFound());
	}

	/** The judge answers each golden case with exactly its expected score. */
	private void stubJudgeWithTheExpectedScores() throws Exception {
		List<Map<String, Object>> golden;
		try (InputStream in = new ClassPathResource("evals/golden-set.json").getInputStream()) {
			golden = json.readValue(in, new TypeReference<>() {
			});
		}
		for (Map<String, Object> evalCase : golden) {
			@SuppressWarnings("unchecked")
			String title = (String) ((Map<String, Object>) evalCase.get("job")).get("title");
			vendor.stubFor(WireMock.post(urlPathEqualTo("/judge/v1/messages"))
					.withRequestBody(containing("Title: " + title + "\\n"))
					.willReturn(okJson("""
							{"content": [{"type": "text", "text": "{\\"score\\": %d, \\"reasons\\": [\\"fit\\"]}"}],
							 "usage": {"input_tokens": 300, "output_tokens": 20}}""".formatted((Integer) evalCase.get("expectedScore")))));
		}
	}

	private static String jobFitVersion(String marker) {
		return """
				{"system": "You are a recruiter (%s).", "template": "<candidate>{{profile}}</candidate><job_posting>{{job}}</job_posting> Score it.",
				 "outputSchema": {"type": "object", "required": ["score", "reasons"], "properties": {
				   "score": {"type": "integer", "minimum": 0, "maximum": 100},
				   "reasons": {"type": "array", "items": {"type": "string"}}}}}""".formatted(marker);
	}

	private String start(String body) throws Exception {
		return mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/evals/runs").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isAccepted())
				.andExpect(header().exists("Location"))
				.andReturn().getResponse().getContentAsString();
	}

	private String finished(String started) throws Exception {
		String id = JsonPath.read(started, "$.id");
		Instant deadline = Instant.now().plus(60, ChronoUnit.SECONDS);
		while (Instant.now().isBefore(deadline)) {
			String run = mvc.perform(MockMvcRequestBuilders.get("/api/v1/admin/evals/runs/" + id)).andReturn().getResponse()
					.getContentAsString();
			String status = JsonPath.read(run, "$.status");
			if (status.equals("SUCCEEDED") || status.equals("FAILED")) {
				return run;
			}
			Thread.sleep(200);
		}
		throw new AssertionError("Eval did not finish");
	}

}
