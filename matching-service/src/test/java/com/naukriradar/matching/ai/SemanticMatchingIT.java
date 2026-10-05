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
import com.naukriradar.matching.embedding.HashingEmbedder;
import com.naukriradar.matching.model.AiProviderType;
import com.naukriradar.matching.repository.AiProviderRepository;
import com.naukriradar.matching.service.AiProviderService;
import com.naukriradar.matching.service.JobEmbeddingIndexer;
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

import static com.github.tomakehurst.wiremock.client.WireMock.absent;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Semantic matching, retrieval and the choice of embedding model, against WireMock services and vendors. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SemanticMatchingIT {

	private static final String USER_HEADER = "X-User-Id";

	private static final String EMBEDDER = "embed-ai";

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

	@Autowired
	private JobEmbeddingIndexer indexer;

	@BeforeEach
	void onlyTheLocalEmbedder() {
		if (!providerRepository.existsByName(EMBEDDER)) {
			providers.create(new AiProviderCreateRequest(EMBEDDER, AiProviderType.OPENAI, services.baseUrl() + "/emb",
					"key-emb-123456", "chat-model", null, false, null, null, null, "emb-1"), "test");
		}
		embedderOn(false);
	}

	@Test
	void aJobTheKeywordSearchMissedIsFoundByMeaningAndScored() throws Exception {
		String user = UUID.randomUUID().toString();
		String jobId = UUID.randomUUID().toString();
		stubProfile(user);
		services.stubFor(WireMock.post(urlEqualTo("/internal/v1/jobs/candidates")).willReturn(okJson("[]")));
		services.stubFor(WireMock.get(urlPathEqualTo("/internal/v1/jobs/recent")).withQueryParam("after", absent())
				.willReturn(okJson("[" + microservicesJob(jobId) + "]")));
		services.stubFor(WireMock.get(urlPathEqualTo("/internal/v1/jobs/recent")).withQueryParam("after", WireMock.matching(".+"))
				.willReturn(okJson("[]")));
		services.stubFor(WireMock.post(urlEqualTo("/internal/v1/jobs/by-ids")).willReturn(okJson("[" + microservicesJob(jobId) + "]")));

		JobEmbeddingIndexer.CatchUp catchUp = indexer.catchUp();
		assertThat(catchUp.modelKey()).isEqualTo(HashingEmbedder.MODEL_KEY);
		assertThat(catchUp.jobsEmbedded()).isGreaterThanOrEqualTo(1);
		// a second catch-up finds nothing new to pay for
		assertThat(indexer.catchUp().jobsEmbedded()).isZero();

		runToEnd(user).andExpect(jsonPath("$.status").value("SUCCESS"));

		String page = mvc.perform(MockMvcRequestBuilders.get("/api/v1/me/matches").header(USER_HEADER, user))
				.andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<String>>read(page, "$.items[*].title")).containsExactly("Spring Microservices Engineer");
		String detail = mvc.perform(MockMvcRequestBuilders.get("/api/v1/me/matches/" + JsonPath.read(page, "$.items[0].id"))
				.header(USER_HEADER, user)).andReturn().getResponse().getContentAsString();
		List<String> semantic = JsonPath.read(detail, "$.breakdown[?(@.factor == 'semantic')].detail");
		assertThat(semantic).singleElement().asString().contains("In meaning");
	}

	@Test
	void theChosenEmbeddingModelIsUsedPaidForOnceAndTheLocalOneStandsInWhenItFails() throws Exception {
		embedderOn(true);
		services.stubFor(WireMock.post(urlPathEqualTo("/emb/v1/embeddings")).willReturn(okJson("""
				{"data": [{"index": 2, "embedding": [0, 1, 0]}, {"index": 0, "embedding": [1, 0, 0]},
				          {"index": 1, "embedding": [0.9, 0.1, 0]}],
				 "usage": {"prompt_tokens": 30}}""")));
		String query = "event streaming " + UUID.randomUUID();
		String body = retrieveBody(query, "Built pipelines on Kafka", "Sold insurance door to door");
		int before = services.findAll(postRequestedFor(urlPathEqualTo("/emb/v1/embeddings"))).size();

		mvc.perform(retrieve(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.modelKey").value("openai:emb-1"))
				.andExpect(jsonPath("$.passages[0].id").value("p1"));
		mvc.perform(retrieve(body)).andExpect(jsonPath("$.passages[0].id").value("p1"));
		mvc.perform(MockMvcRequestBuilders.get("/api/v1/admin/ai/embeddings"))
				.andExpect(jsonPath("$.currentModel").value("openai:emb-1"))
				.andExpect(jsonPath("$.local").value(false));

		assertThat(services.findAll(postRequestedFor(urlPathEqualTo("/emb/v1/embeddings")))).hasSize(before + 1);
		assertThat(services.findAll(postRequestedFor(urlPathEqualTo("/emb/v1/embeddings"))).getLast().getBodyAsString())
				.contains("\"model\":\"emb-1\"").contains("Built pipelines on Kafka");

		services.stubFor(WireMock.post(urlPathEqualTo("/emb/v1/embeddings")).willReturn(aResponse().withStatus(500)));
		mvc.perform(retrieve(retrieveBody("something else " + UUID.randomUUID(), "Kafka streams", "Cooking")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.modelKey").value(HashingEmbedder.MODEL_KEY))
				.andExpect(jsonPath("$.passages[0].id").value("p1"));
	}

	@Test
	void retrievalRanksTheAnsweringPassageFirstWithTheLocalModel() throws Exception {
		mvc.perform(retrieve("""
				{"query": "Have you worked with Kafka?", "top": 2, "passages": [
				  {"id": "edu", "text": "B.Tech in Computer Science, 2019"},
				  {"id": "kafka", "text": "Built order events on Kafka and Spring Boot at PayFlow"},
				  {"id": "hobby", "text": "Cricket and chess"}]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.modelKey").value(HashingEmbedder.MODEL_KEY))
				.andExpect(jsonPath("$.passages.length()").value(2))
				.andExpect(jsonPath("$.passages[0].id").value("kafka"));
		mvc.perform(retrieve("{\"query\": \"\", \"passages\": []}")).andExpect(status().isBadRequest());
		mvc.perform(retrieve("{\"query\": \"x\", \"passages\": []}")).andExpect(jsonPath("$.passages.length()").value(0));
	}

	@Test
	void anthropicCantBePickedForEmbeddings() throws Exception {
		mvc.perform(MockMvcRequestBuilders.post("/api/v1/admin/ai/providers").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"claude-emb\", \"type\": \"ANTHROPIC\", \"model\": \"m\", \"embeddingModel\": \"x\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail", containsString("no embeddings API")));
		mvc.perform(MockMvcRequestBuilders.get("/api/v1/admin/ai/provider-types"))
				.andExpect(jsonPath("$[?(@.type == 'OPENAI')].supportsEmbeddings").value(true))
				.andExpect(jsonPath("$[?(@.type == 'ANTHROPIC')].supportsEmbeddings").value(false));
	}

	private void embedderOn(boolean on) {
		providers.list().forEach(p -> providers.update(p.name(),
				new AiProviderUpdateRequest(null, null, null, null, on && p.name().equals(EMBEDDER), null, null, null, null), "test"));
	}

	private static MockHttpServletRequestBuilder retrieve(String body) {
		return MockMvcRequestBuilders.post("/internal/v1/ai/retrieve").contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private static String retrieveBody(String query, String first, String second) {
		return """
				{"query": "%s", "passages": [{"id": "p1", "text": "%s"}, {"id": "p2", "text": "%s"}]}"""
				.formatted(query, first, second);
	}

	private ResultActions runToEnd(String user) throws Exception {
		String runId = JsonPath.read(mvc.perform(MockMvcRequestBuilders.post("/api/v1/me/matches/runs").header(USER_HEADER, user))
				.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.id");
		Instant deadline = Instant.now().plus(20, ChronoUnit.SECONDS);
		while (Instant.now().isBefore(deadline)) {
			ResultActions result = mvc.perform(MockMvcRequestBuilders.get("/api/v1/me/matches/runs/" + runId).header(USER_HEADER, user));
			if (!"RUNNING".equals(JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.status"))) {
				return result;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Run did not finish");
	}

	private static void stubProfile(String user) {
		services.stubFor(WireMock.get(urlEqualTo("/internal/v1/users/" + user + "/matching-profile")).willReturn(okJson("""
				{"userId": "%s", "skills": ["java", "spring boot"], "targetRoles": ["Java Backend Developer"],
				 "preferredLocations": [], "remoteOk": true, "experienceYears": 3,
				 "excludedCompanies": [], "excludedKeywords": [], "minMatchScore": 40}
				""".formatted(user))));
	}

	private static String microservicesJob(String id) {
		return """
				{"id": "%s", "title": "Spring Microservices Engineer", "company": "Finlytics", "location": "Remote", "remote": true,
				 "postedAt": "%s", "applyUrl": "https://jobs.example.com/%s",
				 "description": "Build microservices with Spring Boot, Spring Cloud and Kafka. REST APIs on the JVM."}
				""".formatted(id, Instant.now().minus(2, ChronoUnit.HOURS), id);
	}

}
