package com.naukriradar.job.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import com.naukriradar.job.normalizer.NormalizedJob;
import com.naukriradar.job.repository.JobBatchWriter;
import com.naukriradar.job.repository.JobBatchWriter.FingerprintedJob;
import com.naukriradar.job.support.TestJobs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Jobs parsed once with AI, against a fake matching-service. */
@SpringBootTest(properties = "naukriradar.jobs.parsing.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobParsingIT {

	@RegisterExtension
	static WireMockExtension matching = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	private static final String PARSED = """
			{"provider": "fake", "model": "m", "costUsd": 0.0001, "answer": {
			  "requiredSkills": ["Java", "Spring Boot", "Kafka"], "minYearsExperience": 4,
			  "seniority": "senior", "workMode": "hybrid", "summary": "Backend role."}}""";

	@DynamicPropertySource
	static void pointAtFakeAi(DynamicPropertyRegistry registry) {
		registry.add("naukriradar.jobs.parsing.matching-service-url", matching::baseUrl);
	}

	@Autowired
	private JobParsingService parsing;

	@Autowired
	private JobBatchWriter writer;

	@Autowired
	private FingerprintService fingerprints;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private MockMvc mvc;

	private String source;

	@BeforeEach
	void freshJobs() {
		// only this test's jobs are unparsed
		jdbc.update("UPDATE jobs SET parsed_at = CURRENT_TIMESTAMP WHERE parsed_at IS NULL");
		source = "parse-" + UUID.randomUUID().toString().substring(0, 8);
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		NormalizedJob job = TestJobs.job("p1", "Senior Java Engineer", "Acme " + source, "Berlin", false, now);
		writer.write(source, List.of(new FingerprintedJob(job, fingerprints.fingerprint(job))), now);
	}

	@Test
	void aJobIsParsedOnceAndTheRequirementsShowUpInDetailAndForMatching() throws Exception {
		matching.stubFor(post(urlPathEqualTo("/internal/v1/ai/run")).willReturn(okJson(PARSED)));

		assertThat(parsing.parsePending()).isEqualTo(1);
		assertThat(parsing.parsePending()).isZero();

		matching.verify(1, postRequestedFor(urlPathEqualTo("/internal/v1/ai/run"))
				.withRequestBody(equalToJson("{\"prompt\": \"job-parse\"}", true, true)));
		String id = jdbc.queryForObject("SELECT id FROM jobs WHERE source_code = ?", String.class, source);
		mvc.perform(get("/api/v1/jobs/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.requirements.requiredSkills", hasItem("Spring Boot")))
				.andExpect(jsonPath("$.requirements.seniority").value("senior"))
				.andExpect(jsonPath("$.parsedAt").exists());
		String candidates = mvc.perform(mvcPost("/internal/v1/jobs/candidates").contentType(MediaType.APPLICATION_JSON)
				.content("{\"keywords\": [\"java\"], \"limit\": 300}"))
				.andReturn().getResponse().getContentAsString();
		List<List<String>> skills = JsonPath.read(candidates, "$[?(@.id == '" + id + "')].requiredSkills");
		List<Integer> years = JsonPath.read(candidates, "$[?(@.id == '" + id + "')].minYearsExperience");
		assertThat(skills).containsExactly(List.of("Java", "Spring Boot", "Kafka"));
		assertThat(years).containsExactly(4);
	}

	@Test
	void withoutAiJobsStayUnparsedAndAreTriedAgainLater() {
		matching.stubFor(post(urlPathEqualTo("/internal/v1/ai/run")).willReturn(okJson(
				"{\"answer\": null, \"unavailableReason\": \"No AI provider is ready.\"}")));

		assertThat(parsing.parsePending()).isZero();

		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jobs WHERE source_code = ? AND parsed_at IS NULL",
				Integer.class, source)).isEqualTo(1);
		matching.stubFor(post(urlPathEqualTo("/internal/v1/ai/run")).willReturn(okJson(PARSED)));
		assertThat(parsing.parsePending()).isEqualTo(1);
	}

	/** MockMvc's post; WireMock's post is the one imported. */
	private static MockHttpServletRequestBuilder mvcPost(String path) {
		return MockMvcRequestBuilders.post(path);
	}

	@Test
	void reparseQueuesEveryActiveJobAgain() throws Exception {
		matching.stubFor(post(urlPathEqualTo("/internal/v1/ai/run")).willReturn(okJson(PARSED)));
		parsing.parsePending();

		mvc.perform(mvcPost("/api/v1/admin/jobs/reparse"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.queued").isNumber());

		for (int i = 0; i < 100 && jdbc.queryForObject(
				"SELECT COUNT(*) FROM jobs WHERE source_code = ? AND parsed_at IS NOT NULL", Integer.class, source) == 0; i++) {
			Thread.sleep(50);
		}
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jobs WHERE source_code = ? AND parsed_at IS NOT NULL",
				Integer.class, source)).isEqualTo(1);
	}

}
