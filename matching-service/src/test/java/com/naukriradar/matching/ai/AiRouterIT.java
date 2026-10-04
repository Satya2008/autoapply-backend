package com.naukriradar.matching.ai;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import com.naukriradar.matching.dto.request.PromptVersionRequest;
import com.naukriradar.matching.model.AiUsage;
import com.naukriradar.matching.repository.AiUsageRepository;
import com.naukriradar.matching.service.PromptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The router against fake providers: see the providers and routes in application-test.yml. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiRouterIT {

	@RegisterExtension
	static WireMockExtension providers = WireMockExtension.newInstance().options(wireMockConfig().port(18093)).build();

	private static final String ANTHROPIC_OK = """
			{"content": [{"type": "text", "text": "{\\"status\\": \\"ok\\", \\"message\\": \\"Ready.\\"}"}],
			 "usage": {"input_tokens": 100, "output_tokens": 20}}""";

	@Autowired
	private MockMvc mvc;

	@Autowired
	private AiRouter router;

	@Autowired
	private PromptService prompts;

	@Autowired
	private AiUsageRepository usage;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void providersAnswer() {
		providers.stubFor(post(urlPathEqualTo("/good/v1/messages")).willReturn(okJson(ANTHROPIC_OK)));
		providers.stubFor(post(urlPathEqualTo("/down/v1/messages")).willReturn(aResponse().withStatus(500)));
		providers.stubFor(post(urlPathEqualTo("/backup/v1/chat/completions")).willReturn(okJson(openAi(
				"{\\\"status\\\": \\\"ok\\\", \\\"message\\\": \\\"Backup here.\\\"}"))));
		providers.stubFor(post(urlPathEqualTo("/junk/v1/chat/completions")).willReturn(okJson(openAi(
				"Sure! Here is what you asked for."))));
	}

	@Test
	void theTestEndpointAnswersThroughTheRouteAndRecordsTheCost() throws Exception {
		mvc.perform(adminPost("/api/v1/admin/ai/test")
				.contentType(MediaType.APPLICATION_JSON).content("{\"topic\": \"Java\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.provider").value("good"))
				.andExpect(jsonPath("$.model").value("model-a"))
				.andExpect(jsonPath("$.answer.status").value("ok"))
				.andExpect(jsonPath("$.costUsd").value(0.0002))
				.andExpect(jsonPath("$.fallbacks").value(0));

		providers.verify(postRequestedFor(urlPathEqualTo("/good/v1/messages")));
		assertThat(providers.findAll(postRequestedFor(urlPathEqualTo("/good/v1/messages"))).getLast().getBodyAsString())
				.contains("one short sentence about Java").contains("JSON Schema");
		assertThat(providers.findAll(postRequestedFor(urlPathEqualTo("/nokey/v1beta/models/m0:generateContent")))).isEmpty();
		mvc.perform(get("/api/v1/admin/ai/usage").param("groupBy", "provider"))
				.andExpect(jsonPath("$[*].group", hasItem("good")));
		mvc.perform(get("/api/v1/admin/ai/usage").param("groupBy", "nonsense")).andExpect(status().isBadRequest());
	}

	@Test
	void aProviderThatIsDownIsSkippedAndItsCircuitOpens() throws Exception {
		prompts.createVersion("fallback-check", new PromptVersionRequest(null, "Say ok.", schema()));

		for (int i = 0; i < 5; i++) {
			AiResult result = router.run("fallback-check", Map.of(), null);
			assertThat(result.provider()).isEqualTo("backup");
			assertThat(result.json().get("message").asString()).isEqualTo("Backup here.");
			assertThat(result.fallbacks()).isEqualTo(1);
		}

		// 3 attempts per run until 10 failures open the circuit; after that "down" isn't called
		assertThat(providers.findAll(postRequestedFor(urlPathEqualTo("/down/v1/messages")))).hasSize(10);
		String states = mvc.perform(get("/api/v1/admin/resilience")).andReturn().getResponse().getContentAsString();
		List<String> downState = JsonPath.read(states, "$[?(@.name == 'ai-down')].state");
		assertThat(downState).containsExactly("OPEN");
	}

	@Test
	void anAnswerThatIsNotTheAskedShapeIsRejectedNotCrashed() throws Exception {
		prompts.createVersion("junk-check", new PromptVersionRequest(null, "Say ok.", schema()));
		long failedBefore = usage.findAll().stream().filter(u -> u.getProvider().equals("junk") && !u.isSuccess()).count();

		assertThatThrownBy(() -> router.run("junk-check", Map.of(), null)).isInstanceOf(AiUnavailableException.class)
				.hasMessageContaining("not valid JSON");

		long failedAfter = usage.findAll().stream().filter(u -> u.getProvider().equals("junk") && !u.isSuccess()).count();
		assertThat(failedAfter).isEqualTo(failedBefore + 1);
		mvc.perform(adminPost("/api/v1/admin/ai/test")
				.contentType(MediaType.APPLICATION_JSON).content("{\"provider\": \"junk\", \"model\": \"model-b\"}"))
				.andExpect(status().isServiceUnavailable());
	}

	@Test
	void aUserOverTodaysBudgetGetsNoAiCalls() {
		String user = UUID.randomUUID().toString();
		usage.save(new AiUsage(user, "job-fit", "good", "model-a", 1, 1, 600_000, 10, true, Instant.now()));
		int before = providers.findAll(postRequestedFor(urlPathEqualTo("/good/v1/messages"))).size();

		assertThatThrownBy(() -> router.run("ai-test", Map.of("topic", "x"), user))
				.isInstanceOf(AiBudgetExceededException.class).hasMessageContaining("budget");
		assertThat(providers.findAll(postRequestedFor(urlPathEqualTo("/good/v1/messages")))).hasSize(before);

		assertThat(router.run("ai-test", Map.of("topic", "x"), UUID.randomUUID().toString()).provider()).isEqualTo("good");
	}

	@Test
	void promptsAreVersionedAndAnOldVersionCanBeActivatedAgain() throws Exception {
		String code = "prompt-" + UUID.randomUUID().toString().substring(0, 8);
		mvc.perform(adminPost("/api/v1/admin/prompts/" + code + "/versions")
				.contentType(MediaType.APPLICATION_JSON).content("{\"template\": \"v1 {{name}}\"}"))
				.andExpect(status().isCreated()).andExpect(jsonPath("$.version").value(1)).andExpect(jsonPath("$.active").value(true));
		mvc.perform(adminPost("/api/v1/admin/prompts/" + code + "/versions")
				.contentType(MediaType.APPLICATION_JSON).content("{\"template\": \"v2 {{name}}\"}"))
				.andExpect(jsonPath("$.version").value(2)).andExpect(jsonPath("$.active").value(false));
		assertThat(prompts.active(code).template()).isEqualTo("v1 {{name}}");

		mvc.perform(adminPost("/api/v1/admin/prompts/" + code + "/versions/2/activate"))
				.andExpect(jsonPath("$.active").value(true));
		assertThat(prompts.active(code).template()).isEqualTo("v2 {{name}}");
		mvc.perform(adminPost("/api/v1/admin/prompts/" + code + "/versions/1/activate"));
		assertThat(prompts.active(code).version()).isEqualTo(1);

		mvc.perform(get("/api/v1/admin/prompts")).andExpect(jsonPath("$[*].code", hasItem("job-fit")));
		mvc.perform(adminPost("/api/v1/admin/prompts/" + code + "/versions/9/activate"))
				.andExpect(status().isNotFound());
		mvc.perform(adminPost("/api/v1/admin/prompts/Bad Code/versions")
				.contentType(MediaType.APPLICATION_JSON).content("{\"template\": \"x\"}"))
				.andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prompts WHERE code = ? AND active", Integer.class, code)).isEqualTo(1);
	}

	/** MockMvc's post; WireMock's post is the one imported. */
	private static MockHttpServletRequestBuilder adminPost(String path) {
		return MockMvcRequestBuilders.post(path);
	}

	private static Map<String, Object> schema() {
		return Map.of("type", "object", "required", List.of("status", "message"), "properties",
				Map.of("status", Map.of("type", "string"), "message", Map.of("type", "string")));
	}

	private static String openAi(String content) {
		return "{\"choices\": [{\"message\": {\"role\": \"assistant\", \"content\": \"" + content + "\"}}],"
				+ " \"usage\": {\"prompt_tokens\": 50, \"completion_tokens\": 10}}";
	}

}
