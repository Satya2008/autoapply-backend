package com.naukriradar.matching.ai;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import com.naukriradar.matching.dto.request.AiProviderCreateRequest;
import com.naukriradar.matching.dto.request.AiProviderUpdateRequest;
import com.naukriradar.matching.dto.request.PromptVersionRequest;
import com.naukriradar.matching.model.AiProviderType;
import com.naukriradar.matching.model.AiUsage;
import com.naukriradar.matching.repository.AiProviderRepository;
import com.naukriradar.matching.repository.AiUsageRepository;
import com.naukriradar.matching.service.AiProviderService;
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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** AI providers set up at runtime, against fake vendors on WireMock. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiRouterIT {

	@RegisterExtension
	static WireMockExtension vendors = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

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
	private AiProviderService providers;

	@Autowired
	private AiProviderRepository providerRepository;

	@Autowired
	private AiUsageRepository usage;

	@Autowired
	private JdbcTemplate jdbc;

	@BeforeEach
	void vendorsAnswer() {
		vendors.stubFor(post(urlPathEqualTo("/good/v1/messages")).willReturn(okJson(ANTHROPIC_OK)));
		vendors.stubFor(post(urlPathEqualTo("/down/v1/messages")).willReturn(aResponse().withStatus(500)));
		vendors.stubFor(post(urlPathEqualTo("/backup/v1/chat/completions")).willReturn(okJson(openAi(
				"{\\\"status\\\": \\\"ok\\\", \\\"message\\\": \\\"Backup here.\\\"}"))));
		vendors.stubFor(post(urlPathEqualTo("/junk/v1/chat/completions")).willReturn(okJson(openAi(
				"Sure! Here is what you asked for."))));
		provider("good", AiProviderType.ANTHROPIC, "model-a");
		provider("down", AiProviderType.ANTHROPIC, "model-a");
		provider("backup", AiProviderType.OPENAI, "model-b");
		provider("junk", AiProviderType.OPENAI, "model-b");
	}

	@Test
	void theTestEndpointUsesThePrimaryAndRecordsTheCost() throws Exception {
		providers.create(new AiProviderCreateRequest("nokey-" + suffix(), AiProviderType.GEMINI, url("/nokey"), null, "m0", null,
				true, null, null, null), "t");
		use("good");

		mvc.perform(adminPost("/api/v1/admin/ai/test").contentType(MediaType.APPLICATION_JSON).content("{\"topic\": \"Java\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.provider").value("good"))
				.andExpect(jsonPath("$.model").value("model-a"))
				.andExpect(jsonPath("$.answer.status").value("ok"))
				.andExpect(jsonPath("$.costUsd").value(0.0002))
				.andExpect(jsonPath("$.fallbacks").value(0));

		String sent = vendors.findAll(postRequestedFor(urlPathEqualTo("/good/v1/messages"))).getLast().getBodyAsString();
		assertThat(sent).contains("one short sentence about Java").contains("JSON Schema");
		assertThat(vendors.findAll(postRequestedFor(urlPathEqualTo("/good/v1/messages"))).getLast().getHeader("x-api-key"))
				.isEqualTo("key-good-123456");
		mvc.perform(adminGet("/api/v1/admin/ai/usage").param("groupBy", "provider"))
				.andExpect(jsonPath("$[*].group", hasItem("good")));
		mvc.perform(adminGet("/api/v1/admin/ai/usage").param("groupBy", "nonsense")).andExpect(status().isBadRequest());
	}

	@Test
	void switchingThePrimaryChangesWhoAnswersOnTheNextCall() {
		use("good", "backup");
		assertThat(router.run("ai-test", Map.of("topic", "x"), null, true).provider()).isEqualTo("good");

		providers.makePrimary("backup", "t");

		assertThat(router.run("ai-test", Map.of("topic", "x"), null, true).provider()).isEqualTo("backup");
	}

	@Test
	void aProviderThatIsDownIsSkippedAndItsCircuitOpens() throws Exception {
		prompts.createVersion("fallback-check", new PromptVersionRequest(null, "Say ok.", schema()));
		use("down", "backup");

		for (int i = 0; i < 5; i++) {
			AiResult result = router.run("fallback-check", Map.of(), null, true);
			assertThat(result.provider()).isEqualTo("backup");
			assertThat(result.json().get("message").asString()).isEqualTo("Backup here.");
			assertThat(result.fallbacks()).isEqualTo(1);
		}

		// 3 attempts per run until 10 failures open the circuit; after that "down" isn't called
		assertThat(vendors.findAll(postRequestedFor(urlPathEqualTo("/down/v1/messages")))).hasSize(10);
		String states = mvc.perform(adminGet("/api/v1/admin/resilience")).andReturn().getResponse().getContentAsString();
		List<String> downState = JsonPath.read(states, "$[?(@.name == 'ai-down')].state");
		assertThat(downState).containsExactly("OPEN");
	}

	@Test
	void anAnswerThatIsNotTheAskedShapeIsRejectedNotCrashed() throws Exception {
		prompts.createVersion("junk-check", new PromptVersionRequest(null, "Say ok.", schema()));
		use("junk");
		long failedBefore = failedCalls("junk");

		assertThatThrownBy(() -> router.run("junk-check", Map.of(), null)).isInstanceOf(AiUnavailableException.class)
				.hasMessageContaining("not valid JSON");

		assertThat(failedCalls("junk")).isEqualTo(failedBefore + 1);
		mvc.perform(adminPost("/api/v1/admin/ai/test").contentType(MediaType.APPLICATION_JSON)
				.content("{\"provider\": \"junk\"}"))
				.andExpect(status().isServiceUnavailable());
	}

	@Test
	void withNoProviderReadyTheAnswerSaysWhatToDo() {
		use();

		assertThatThrownBy(() -> router.run("ai-test", Map.of("topic", "x"), null, true))
				.isInstanceOf(AiUnavailableException.class).hasMessageContaining("/api/v1/admin/ai/providers");
	}

	@Test
	void aProviderCanBeTriedBeforeItIsSwitchedOn() throws Exception {
		use("good");

		mvc.perform(adminPost("/api/v1/admin/ai/test").contentType(MediaType.APPLICATION_JSON)
				.content("{\"provider\": \"backup\", \"model\": \"model-x\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.provider").value("backup"))
				.andExpect(jsonPath("$.model").value("model-x"));
	}

	@Test
	void theSameQuestionIsPaidForOnce() {
		use("good");
		String topic = "cache " + suffix();
		int before = vendors.findAll(postRequestedFor(urlPathEqualTo("/good/v1/messages"))).size();

		AiResult first = router.run("ai-test", Map.of("topic", topic), null);
		AiResult second = router.run("ai-test", Map.of("topic", topic), null);
		router.run("ai-test", Map.of("topic", topic), null, true);

		assertThat(second.json()).isEqualTo(first.json());
		assertThat(vendors.findAll(postRequestedFor(urlPathEqualTo("/good/v1/messages")))).hasSize(before + 2);
	}

	@Test
	void aUserOverTodaysBudgetGetsNoAiCalls() {
		use("good");
		String user = UUID.randomUUID().toString();
		usage.save(new AiUsage(user, "job-fit", "good", "model-a", 1, 1, 600_000, 10, true, Instant.now()));
		int before = vendors.findAll(postRequestedFor(urlPathEqualTo("/good/v1/messages"))).size();

		assertThatThrownBy(() -> router.run("ai-test", Map.of("topic", "x"), user, true))
				.isInstanceOf(AiBudgetExceededException.class).hasMessageContaining("budget");
		assertThat(vendors.findAll(postRequestedFor(urlPathEqualTo("/good/v1/messages")))).hasSize(before);

		assertThat(router.run("ai-test", Map.of("topic", "x"), UUID.randomUUID().toString(), true).provider()).isEqualTo("good");
	}

	@Test
	void providersAreManagedThroughTheApiAndKeysNeverComeBack() throws Exception {
		String name = "groq-" + suffix();
		String body = "{\"name\": \"" + name + "\", \"type\": \"OPENAI\", \"baseUrl\": \"https://api.groq.com/openai/\","
				+ " \"apiKey\": \"gsk-secret-abcdef9876\", \"model\": \"llama-3.3-70b-versatile\", \"enabled\": false}";
		String created = mvc.perform(adminPost("/api/v1/admin/ai/providers").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.baseUrl").value("https://api.groq.com/openai"))
				.andExpect(jsonPath("$.apiKeySet").value(true))
				.andExpect(jsonPath("$.apiKeyHint").value("…9876"))
				.andExpect(jsonPath("$.ready").value(false))
				.andReturn().getResponse().getContentAsString();
		assertThat(created).doesNotContain("gsk-secret");
		String stored = jdbc.queryForObject("SELECT api_key FROM ai_providers WHERE name = ?", String.class, name);
		assertThat(stored).startsWith("v1:").doesNotContain("gsk-secret");

		mvc.perform(adminPost("/api/v1/admin/ai/providers").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isConflict());
		mvc.perform(patch("/api/v1/admin/ai/providers/" + name).contentType(MediaType.APPLICATION_JSON)
				.content("{\"enabled\": true, \"model\": \"llama-3.1-8b-instant\"}"))
				.andExpect(jsonPath("$.ready").value(true))
				.andExpect(jsonPath("$.model").value("llama-3.1-8b-instant"))
				.andExpect(jsonPath("$.apiKeySet").value(true));
		mvc.perform(adminPost("/api/v1/admin/ai/providers/" + name + "/primary"))
				.andExpect(jsonPath("$[0].name").value(name))
				.andExpect(jsonPath("$[0].primary").value(true));
		mvc.perform(put("/api/v1/admin/ai/providers/order").contentType(MediaType.APPLICATION_JSON)
				.content("{\"names\": [\"good\", \"" + name + "\"]}"))
				.andExpect(jsonPath("$[0].name").value("good"))
				.andExpect(jsonPath("$[1].name").value(name));
		mvc.perform(patch("/api/v1/admin/ai/providers/" + name).contentType(MediaType.APPLICATION_JSON)
				.content("{\"apiKey\": \"\"}"))
				.andExpect(jsonPath("$.apiKeySet").value(false))
				.andExpect(jsonPath("$.ready").value(false));

		mvc.perform(delete("/api/v1/admin/ai/providers/" + name)).andExpect(status().isNoContent());
		mvc.perform(adminGet("/api/v1/admin/ai/providers")).andExpect(jsonPath("$[*].name", not(hasItem(name))));
		mvc.perform(adminPost("/api/v1/admin/ai/providers").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"x\", \"type\": \"NOPE\", \"model\": \"m\"}"))
				.andExpect(status().isBadRequest());
		mvc.perform(adminPost("/api/v1/admin/ai/providers/nope/primary")).andExpect(status().isNotFound());
		mvc.perform(adminGet("/api/v1/admin/ai/provider-types"))
				.andExpect(jsonPath("$", hasSize(4)))
				.andExpect(jsonPath("$[?(@.type == 'OLLAMA')].needsApiKey", contains(false)));
	}

	@Test
	void theModelsOfAProviderComeFromTheVendor() throws Exception {
		vendors.stubFor(get(urlPathEqualTo("/good/v1/models")).willReturn(okJson(
				"{\"data\": [{\"id\": \"model-z\"}, {\"id\": \"model-a\"}]}")));

		mvc.perform(adminGet("/api/v1/admin/ai/providers/good/models"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", contains("model-a", "model-z")));

		String keyless = "keyless-" + suffix();
		providers.create(new AiProviderCreateRequest(keyless, AiProviderType.OPENAI, url("/x"), null, "m", null, true, null, null,
				null), "t");
		mvc.perform(adminGet("/api/v1/admin/ai/providers/" + keyless + "/models")).andExpect(status().isServiceUnavailable());
	}

	@Test
	void promptsAreVersionedAndAnOldVersionCanBeActivatedAgain() throws Exception {
		String code = "prompt-" + suffix();
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

		mvc.perform(adminGet("/api/v1/admin/prompts")).andExpect(jsonPath("$[*].code", hasItem("job-fit")));
		mvc.perform(adminPost("/api/v1/admin/prompts/" + code + "/versions/9/activate")).andExpect(status().isNotFound());
		mvc.perform(adminPost("/api/v1/admin/prompts/Bad Code/versions")
				.contentType(MediaType.APPLICATION_JSON).content("{\"template\": \"x\"}"))
				.andExpect(status().isBadRequest());
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM prompts WHERE code = ? AND active", Integer.class, code))
				.isEqualTo(1);
	}

	/** Adds a fake vendor account once; its key is "key-name-123456". */
	private void provider(String name, AiProviderType type, String model) {
		if (!providerRepository.existsByName(name)) {
			providers.create(new AiProviderCreateRequest(name, type, url("/" + name), "key-" + name + "-123456", model, null, true,
					null, null, null), "test");
		}
	}

	/** Only these providers on, tried in this order. */
	private void use(String... names) {
		List<String> wanted = List.of(names);
		providers.list().forEach(p -> providers.update(p.name(),
				new AiProviderUpdateRequest(null, null, null, null, wanted.contains(p.name()), null, null, null), "test"));
		if (!wanted.isEmpty()) {
			providers.reorder(wanted);
		}
	}

	private long failedCalls(String provider) {
		return usage.findAll().stream().filter(u -> u.getProvider().equals(provider) && !u.isSuccess()).count();
	}

	private static String url(String path) {
		return "http://localhost:" + vendors.getPort() + path;
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	/** MockMvc's post; WireMock's post is the one imported. */
	private static MockHttpServletRequestBuilder adminPost(String path) {
		return MockMvcRequestBuilders.post(path);
	}

	/** MockMvc's get, for the same reason. */
	private static MockHttpServletRequestBuilder adminGet(String path) {
		return MockMvcRequestBuilders.get(path);
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
