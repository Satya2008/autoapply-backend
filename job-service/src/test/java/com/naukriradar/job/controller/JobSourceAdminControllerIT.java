package com.naukriradar.job.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full stack against MySQL, with a WireMock server standing in for the job board. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobSourceAdminControllerIT {

	private static final String SOURCES = "/api/v1/admin/job-sources";

	@RegisterExtension
	static WireMockExtension board = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@Autowired
	private MockMvc mvc;

	@Test
	void defaultBoardIsSeededOnStartup() throws Exception {
		mvc.perform(get(SOURCES))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].code", hasItem("arbeitnow")));
	}

	@Test
	void createRejectsDuplicatesAndBrokenConfig() throws Exception {
		String code = uniqueCode();
		create(code, "/jobs", 1, "{}").andExpect(status().isCreated())
				.andExpect(jsonPath("$.lastRunStatus").value("NEVER"))
				.andExpect(jsonPath("$.jobCount").value(0));

		create(code, "/jobs", 1, "{}")
				.andExpect(status().isConflict())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

		mvc.perform(post(SOURCES).contentType(MediaType.APPLICATION_JSON)
				.content(sourceJson(uniqueCode(), "/jobs", 1, "{}").replace("\"$.data\"", "\"data[\"")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("resultsPath")));

		mvc.perform(post(SOURCES).contentType(MediaType.APPLICATION_JSON)
				.content(sourceJson("Bad Code!", "/jobs", 1, "{}")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors.code").exists());
	}

	@Test
	void dryRunShowsWhatWouldBeSavedWithoutSaving() throws Exception {
		String path = "/" + uniqueCode();
		stubPages(path);
		String id = idOf(create(uniqueCode(), path, 2, "{}"));

		mvc.perform(post(SOURCES + "/" + id + "/test"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ok").value(true))
				.andExpect(jsonPath("$.received").value(3))
				.andExpect(jsonPath("$.valid").value(2))
				.andExpect(jsonPath("$.skipped").value(1))
				.andExpect(jsonPath("$.sample", hasSize(2)))
				.andExpect(jsonPath("$.sample[0].title").value("Java Backend Developer (m/w/d)"))
				.andExpect(jsonPath("$.sample[0].postedAt").value("2026-09-21T14:13:20Z"))
				.andExpect(jsonPath("$.sample[1].remote").value(true))
				.andExpect(jsonPath("$.problems[0]").value(containsString("applyUrl")));

		getSource(id)
				.andExpect(jsonPath("$.jobCount").value(0))
				.andExpect(jsonPath("$.lastRunStatus").value("NEVER"));
	}

	@Test
	void fetchSavesNewJobsAndUpdatesKnownOnes() throws Exception {
		String path = "/" + uniqueCode();
		stubPages(path);
		String id = idOf(create(uniqueCode(), path, 2, "{}"));

		fetch(id)
				.andExpect(jsonPath("$.status").value("SUCCESS"))
				.andExpect(jsonPath("$.pagesFetched").value(2))
				.andExpect(jsonPath("$.inserted").value(2))
				.andExpect(jsonPath("$.updated").value(0))
				.andExpect(jsonPath("$.skipped").value(1));

		fetch(id)
				.andExpect(jsonPath("$.inserted").value(0))
				.andExpect(jsonPath("$.updated").value(2));

		getSource(id)
				.andExpect(jsonPath("$.jobCount").value(2))
				.andExpect(jsonPath("$.lastRunStatus").value("SUCCESS"))
				.andExpect(jsonPath("$.lastRunMessage").value(containsString("0 new, 2 updated")));
	}

	@Test
	void boardThatIgnoresThePageParameterIsNotReadForever() throws Exception {
		String path = "/" + uniqueCode();
		board.stubFor(WireMock.get(urlPathEqualTo(path)).willReturn(okJson(fixture(path))));
		String id = idOf(create(uniqueCode(), path, 5, "{}"));

		fetch(id)
				.andExpect(jsonPath("$.status").value("SUCCESS"))
				.andExpect(jsonPath("$.pagesFetched").value(2))
				.andExpect(jsonPath("$.received").value(3))
				.andExpect(jsonPath("$.inserted").value(2));
	}

	@Test
	void failingBoardIsRecordedAndSwitchedOffAfterRepeatedFailures() throws Exception {
		String path = "/" + uniqueCode();
		board.stubFor(WireMock.get(urlPathEqualTo(path)).willReturn(aResponse().withStatus(500)));
		String id = idOf(create(uniqueCode(), path, 1, "{}"));

		fetch(id)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.message").value(containsString("HTTP 500")))
				.andExpect(jsonPath("$.sourceDisabled").value(false));
		fetch(id).andExpect(jsonPath("$.sourceDisabled").value(false));
		// test profile disables after 3 failures in a row
		fetch(id).andExpect(jsonPath("$.sourceDisabled").value(true));

		getSource(id)
				.andExpect(jsonPath("$.enabled").value(false))
				.andExpect(jsonPath("$.consecutiveFailures").value(3))
				.andExpect(jsonPath("$.lastRunStatus").value("FAILED"));

		board.stubFor(WireMock.get(urlPathEqualTo(path)).willReturn(okJson("{\"data\": []}")));
		fetch(id).andExpect(jsonPath("$.status").value("SUCCESS"));
		getSource(id).andExpect(jsonPath("$.consecutiveFailures").value(0));
	}

	@Test
	void literalHeaderSecretsAreMaskedAndKeptOnUpdate() throws Exception {
		String path = "/" + uniqueCode();
		String code = uniqueCode();
		board.stubFor(WireMock.get(urlPathEqualTo(path)).willReturn(okJson("{\"data\": []}")));
		String headers = "{\"X-Api-Key\": \"plain-secret\", \"Authorization\": \"Bearer ${setting:test-api-key}\"}";
		String id = idOf(create(code, path, 1, headers)
				.andExpect(jsonPath("$.headers.X-Api-Key").value("****"))
				.andExpect(jsonPath("$.headers.Authorization").value("Bearer ${setting:test-api-key}")));

		mvc.perform(put(SOURCES + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(sourceJson(code, path, 1, "{\"X-Api-Key\": \"****\", \"Authorization\": \"Bearer ${setting:test-api-key}\"}")))
				.andExpect(status().isOk());
		fetch(id).andExpect(jsonPath("$.status").value("SUCCESS"));

		board.verify(getRequestedFor(urlPathEqualTo(path))
				.withHeader("X-Api-Key", equalTo("plain-secret"))
				.withHeader("Authorization", equalTo("Bearer secret-123")));
	}

	@Test
	void codeCannotBeChanged() throws Exception {
		String id = idOf(create(uniqueCode(), "/jobs", 1, "{}"));

		mvc.perform(put(SOURCES + "/" + id).contentType(MediaType.APPLICATION_JSON)
				.content(sourceJson(uniqueCode(), "/jobs", 1, "{}")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value(containsString("code can't be changed")));
	}

	@Test
	void deleteAndUnknownIds() throws Exception {
		String id = idOf(create(uniqueCode(), "/jobs", 1, "{}"));

		mvc.perform(delete(SOURCES + "/" + id)).andExpect(status().isNoContent());
		getSource(id).andExpect(status().isNotFound());
		mvc.perform(delete(SOURCES + "/" + id)).andExpect(status().isNotFound());
		fetch(UUID.randomUUID().toString()).andExpect(status().isNotFound());
		mvc.perform(post(SOURCES + "/" + UUID.randomUUID() + "/test")).andExpect(status().isNotFound());
	}

	private void stubPages(String path) throws IOException {
		board.stubFor(WireMock.get(urlPathEqualTo(path)).withQueryParam("page", equalTo("1")).willReturn(okJson(fixture(path))));
		board.stubFor(WireMock.get(urlPathEqualTo(path)).withQueryParam("page", equalTo("2")).willReturn(okJson("{\"data\": []}")));
	}

	private ResultActions create(String code, String path, int maxPages, String headersJson) throws Exception {
		return mvc.perform(post(SOURCES).contentType(MediaType.APPLICATION_JSON)
				.content(sourceJson(code, path, maxPages, headersJson)));
	}

	private ResultActions fetch(String id) throws Exception {
		return mvc.perform(post(SOURCES + "/" + id + "/fetch"));
	}

	private ResultActions getSource(String id) throws Exception {
		return mvc.perform(get(SOURCES + "/" + id));
	}

	private static String idOf(ResultActions result) throws Exception {
		return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
	}

	private static String sourceJson(String code, String path, int maxPages, String headersJson) {
		return """
				{
				  "code": "%s",
				  "name": "Test board",
				  "type": "REST_JSON",
				  "baseUrl": "%s",
				  "searchPath": "%s",
				  "method": "GET",
				  "headers": %s,
				  "queryParams": {"page": "{page}"},
				  "resultsPath": "$.data",
				  "fieldMappings": {
				    "externalId": "$.slug",
				    "title": "$.title",
				    "company": "$.company_name",
				    "location": "$.location",
				    "remote": "$.remote",
				    "description": "$.description",
				    "applyUrl": "$.url",
				    "postedAt": "$.created_at"
				  },
				  "enabled": true,
				  "priority": 5,
				  "timeoutSeconds": 5,
				  "maxPages": %d
				}
				""".formatted(code, board.baseUrl(), path, headersJson, maxPages);
	}

	private static String uniqueCode() {
		return "t-" + UUID.randomUUID().toString().substring(0, 8);
	}

	/**
	 * The sample page with company names tagged per test. Jobs are de-duplicated by fingerprint
	 * across the whole table, so every test needs postings no other test has stored.
	 */
	private static String fixture(String tag) throws IOException {
		return new ClassPathResource("boards/arbeitnow-page1.json").getContentAsString(StandardCharsets.UTF_8)
				.replaceAll("(\"company_name\": \")([^\"]+)\"", "$1$2 " + Matcher.quoteReplacement(tag) + "\"");
	}

}
