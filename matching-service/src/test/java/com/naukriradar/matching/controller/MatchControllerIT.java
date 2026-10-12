package com.naukriradar.matching.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import com.naukriradar.common.redis.run.RunLeases;
import com.naukriradar.matching.model.MatchRunStatus;
import com.naukriradar.matching.repository.MatchRunRepository;
import com.naukriradar.matching.service.MatchRunStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full stack against MySQL; WireMock plays both core-api and job-service. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MatchControllerIT {

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

	@Autowired
	private MatchRunStore runStore;

	@Autowired
	private MatchRunRepository runRepository;

	@Autowired
	private RunLeases leases;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void runScoresKeepsTheGoodOnesAndListsThemBestFirst() throws Exception {
		String user = newUser();
		stubProfile(user, profileJson(user, "[\"initech\"]"));
		stubCandidates(jobsJson(perfectJob(), okJob(), poorJob(), job("excluded", "Backend Engineer", "Initech", "Pune")));

		String runId = startRun(user)
				.andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/v1/me/matches/runs/")))
				.andExpect(jsonPath("$.status").value("RUNNING"))
				.andReturn().getResponse().getContentAsString();
		runId = JsonPath.read(runId, "$.id");
		waitForRun(user, runId)
				.andExpect(jsonPath("$.status").value("SUCCESS"))
				.andExpect(jsonPath("$.jobsConsidered").value(4))
				.andExpect(jsonPath("$.excluded").value(1))
				.andExpect(jsonPath("$.matchesCreated").value(2))
				.andExpect(jsonPath("$.belowThreshold").value(1));

		String list = mvc.perform(get("/api/v1/me/matches").header(USER_HEADER, user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items[*].jobId", contains("perfect", "ok")))
				.andExpect(jsonPath("$.items[0].title").value("Backend Engineer"))
				.andReturn().getResponse().getContentAsString();
		String bestId = JsonPath.read(list, "$.items[0].id");

		mvc.perform(get("/api/v1/me/matches/" + bestId).header(USER_HEADER, user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.match.score").value(100))
				.andExpect(jsonPath("$.breakdown", hasSize(6)))
				.andExpect(jsonPath("$.breakdown[0].factor").value("skills"))
				.andExpect(jsonPath("$.breakdown[0].detail").value(startsWith("Mentions 3 of your skills")));

		services.verify(postRequestedFor(urlEqualTo("/internal/v1/jobs/candidates"))
				.withRequestBody(containing("spring boot"))
				.withRequestBody(containing("Backend Engineer")));
	}

	@Test
	void runningAgainRefreshesMatchesInsteadOfDuplicatingThem() throws Exception {
		String user = newUser();
		stubProfile(user, profileJson(user, "[]"));
		stubCandidates(jobsJson(perfectJob(), okJob()));

		runToEnd(user).andExpect(jsonPath("$.matchesCreated").value(2));
		runToEnd(user)
				.andExpect(jsonPath("$.matchesCreated").value(0))
				.andExpect(jsonPath("$.matchesUpdated").value(2));

		mvc.perform(get("/api/v1/me/matches").header(USER_HEADER, user)).andExpect(jsonPath("$.items", hasSize(2)));
	}

	@Test
	void newlyExcludedCompanyDisappearsFromEarlierMatches() throws Exception {
		String user = newUser();
		stubProfile(user, profileJson(user, "[]"));
		stubCandidates(jobsJson(perfectJob(), okJob()));
		runToEnd(user);
		// cached now; the next run must drop it
		mvc.perform(get("/api/v1/me/matches").header(USER_HEADER, user)).andExpect(jsonPath("$.items", hasSize(2)));

		stubProfile(user, profileJson(user, "[\"globex\"]"));
		runToEnd(user).andExpect(jsonPath("$.excluded").value(1));

		mvc.perform(get("/api/v1/me/matches").header(USER_HEADER, user))
				.andExpect(jsonPath("$.items[*].jobId", contains("perfect")));
	}

	@Test
	void secondRunWhileOneIsGoingIsRefused() throws Exception {
		String user = newUser();
		stubProfile(user, profileJson(user, "[]"));
		services.stubFor(WireMock.post(urlEqualTo("/internal/v1/jobs/candidates"))
				.willReturn(okJson(jobsJson(perfectJob())).withFixedDelay(2_000)));

		String runId = JsonPath.read(startRun(user).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(post("/api/v1/me/matches/runs").header(USER_HEADER, user))
				.andExpect(status().isConflict())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));

		waitForRun(user, runId).andExpect(jsonPath("$.status").value("SUCCESS"));
		// once it's done, the user can run again
		startRun(user);
	}

	@Test
	void upstreamProblemsFailTheRunWithAMessageTheUserUnderstands() throws Exception {
		String missing = newUser();
		services.stubFor(WireMock.get(urlEqualTo("/internal/v1/users/" + missing + "/matching-profile"))
				.willReturn(aResponse().withStatus(404)));
		runToEnd(missing)
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.message").value("Your profile was not found."));

		String empty = newUser();
		stubProfile(empty, """
				{"userId": "%s", "skills": [], "targetRoles": [], "remoteOk": false, "minMatchScore": 50}
				""".formatted(empty));
		runToEnd(empty).andExpect(jsonPath("$.message").value("Add skills or target roles to your profile first."));

		String unlucky = newUser();
		stubProfile(unlucky, profileJson(unlucky, "[]"));
		services.stubFor(WireMock.post(urlEqualTo("/internal/v1/jobs/candidates")).willReturn(aResponse().withStatus(503)));
		runToEnd(unlucky)
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.message").value(startsWith("job-service is unavailable")));
	}

	@Test
	void matchesAndRunsArePrivateToTheirUser() throws Exception {
		String owner = newUser();
		stubProfile(owner, profileJson(owner, "[]"));
		stubCandidates(jobsJson(perfectJob()));
		String runId = JsonPath.read(runToEnd(owner).andReturn().getResponse().getContentAsString(), "$.id");
		String matchId = JsonPath.read(mvc.perform(get("/api/v1/me/matches").header(USER_HEADER, owner))
				.andReturn().getResponse().getContentAsString(), "$.items[0].id");

		String stranger = newUser();
		mvc.perform(get("/api/v1/me/matches/" + matchId).header(USER_HEADER, stranger)).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/me/matches/runs/" + runId).header(USER_HEADER, stranger)).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/me/matches").header(USER_HEADER, stranger)).andExpect(jsonPath("$.items", hasSize(0)));
	}

	@Test
	void pagingVisitsEveryMatchOnceAndMinScoreFilters() throws Exception {
		String user = newUser();
		stubProfile(user, profileJson(user, "[]"));
		List<String> jobs = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			jobs.add(job("p" + i, i % 2 == 0 ? "Backend Engineer" : "Java Developer", "Co" + i, i < 3 ? "Pune" : "Delhi"));
		}
		stubCandidates("[" + String.join(",", jobs) + "]");
		runToEnd(user);

		List<Integer> scores = new ArrayList<>();
		List<String> ids = new ArrayList<>();
		String cursor = null;
		do {
			var request = get("/api/v1/me/matches").header(USER_HEADER, user).param("limit", "2");
			if (cursor != null) {
				request.param("cursor", cursor);
			}
			String body = mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			scores.addAll(JsonPath.read(body, "$.items[*].score"));
			ids.addAll(JsonPath.read(body, "$.items[*].id"));
			cursor = JsonPath.read(body, "$.nextCursor");
		}
		while (cursor != null);

		assertThat(ids).hasSize(5).doesNotHaveDuplicates();
		assertThat(scores).isSortedAccordingTo((a, b) -> b - a);
		int top = scores.get(0);
		mvc.perform(get("/api/v1/me/matches").header(USER_HEADER, user).param("minScore", String.valueOf(top)))
				.andExpect(jsonPath("$.items[*].score", everyItem(is(top))));
	}

	@Test
	void internalListGivesCoreApiTheBestMatchesAboveAScore() throws Exception {
		String user = newUser();
		stubProfile(user, profileJson(user, "[]"));
		stubCandidates(jobsJson(perfectJob(), okJob()));
		runToEnd(user);

		mvc.perform(get("/internal/v1/users/" + user + "/matches"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].jobId", contains("perfect", "ok")))
				.andExpect(jsonPath("$[0].applyUrl").value("https://jobs.example.com/perfect"));
		mvc.perform(get("/internal/v1/users/" + user + "/matches").param("minScore", "90"))
				.andExpect(jsonPath("$[*].jobId", contains("perfect")));
		mvc.perform(get("/internal/v1/users/" + user + "/matches").param("limit", "1000"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void badRequestsAreRejected() throws Exception {
		mvc.perform(get("/api/v1/me/matches")).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/me/matches/runs").header(USER_HEADER, "not-a-uuid")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/me/matches").header(USER_HEADER, newUser()).param("limit", "0"))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/me/matches").header(USER_HEADER, newUser()).param("minScore", "101"))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/me/matches").header(USER_HEADER, newUser()).param("cursor", "garbage!"))
				.andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/me/matches/runs/" + UUID.randomUUID()).header(USER_HEADER, newUser()))
				.andExpect(status().isNotFound());
	}

	@Test
	void runLeftRunningByACrashIsClosedAndTheUserCanRunAgain() throws Exception {
		String user = newUser();
		String stuck = runStore.create(user).getId();
		String fresh = runStore.create(newUser()).getId();
		String live = runStore.create(newUser()).getId();
		// lease first: the background sweep may run at any moment
		leases.begin("match", live);
		jdbc.update("UPDATE match_runs SET started_at = started_at - INTERVAL 5 MINUTE WHERE id IN (?, ?)", stuck, live);
		try {
			runStore.closeInterruptedRuns();
		}
		finally {
			leases.end("match", live);
		}

		assertThat(runRepository.findById(stuck).orElseThrow().getStatus()).isEqualTo(MatchRunStatus.FAILED);
		assertThat(runRepository.findById(fresh).orElseThrow().getStatus()).isEqualTo(MatchRunStatus.RUNNING);
		assertThat(runRepository.findById(live).orElseThrow().getStatus()).isEqualTo(MatchRunStatus.RUNNING);
		jdbc.update("UPDATE match_runs SET status = 'FAILED', running_user_id = NULL WHERE id IN (?, ?)", fresh, live);
		stubProfile(user, profileJson(user, "[]"));
		stubCandidates(jobsJson(perfectJob()));
		startRun(user);
	}

	private ResultActions startRun(String user) throws Exception {
		return mvc.perform(post("/api/v1/me/matches/runs").header(USER_HEADER, user)).andExpect(status().isAccepted());
	}

	private ResultActions runToEnd(String user) throws Exception {
		String runId = JsonPath.read(startRun(user).andReturn().getResponse().getContentAsString(), "$.id");
		return waitForRun(user, runId);
	}

	private ResultActions waitForRun(String user, String runId) throws Exception {
		Instant deadline = Instant.now().plus(20, ChronoUnit.SECONDS);
		while (Instant.now().isBefore(deadline)) {
			ResultActions result = mvc.perform(get("/api/v1/me/matches/runs/" + runId).header(USER_HEADER, user));
			String status = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.status");
			if (!status.equals("RUNNING")) {
				return result;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Run " + runId + " didn't finish");
	}

	private static void stubProfile(String user, String json) {
		services.stubFor(WireMock.get(urlEqualTo("/internal/v1/users/" + user + "/matching-profile")).willReturn(okJson(json)));
	}

	private static void stubCandidates(String json) {
		services.stubFor(WireMock.post(urlEqualTo("/internal/v1/jobs/candidates")).willReturn(okJson(json)));
	}

	private static String profileJson(String user, String excludedCompanies) {
		return """
				{"userId": "%s", "skills": ["java", "spring boot", "mysql"], "targetRoles": ["Backend Engineer"],
				 "preferredLocations": ["Pune"], "remoteOk": false, "expectedSalary": 1200000, "experienceYears": 3,
				 "excludedCompanies": %s, "excludedKeywords": [], "minMatchScore": 50}
				""".formatted(user, excludedCompanies);
	}

	private static String perfectJob() {
		return """
				{"id": "perfect", "title": "Backend Engineer", "company": "Acme", "location": "Pune", "remote": false,
				 "salaryMin": 1000000, "salaryMax": 1500000, "currency": "INR", "postedAt": "%s",
				 "applyUrl": "https://jobs.example.com/perfect",
				 "description": "Java, Spring Boot and MySQL. 2-4 years of experience."}
				""".formatted(Instant.now().minus(1, ChronoUnit.HOURS));
	}

	private static String okJob() {
		return job("ok", "Java Developer", "Globex", "Pune");
	}

	private static String poorJob() {
		return """
				{"id": "poor", "title": "Sales Manager", "company": "Hooli", "location": "Chennai", "remote": false,
				 "postedAt": "2026-01-01T00:00:00Z", "applyUrl": "https://jobs.example.com/poor",
				 "description": "Cold calling. 10+ years of experience."}
				""";
	}

	private static String job(String id, String title, String company, String location) {
		return """
				{"id": "%s", "title": "%s", "company": "%s", "location": "%s", "remote": false,
				 "postedAt": "%s", "applyUrl": "https://jobs.example.com/%s", "description": "We use Java and Docker."}
				""".formatted(id, title, company, location, Instant.now().minus(2, ChronoUnit.DAYS), id);
	}

	private static String jobsJson(String... jobs) {
		return "[" + String.join(",", jobs) + "]";
	}

	private static String newUser() {
		return UUID.randomUUID().toString();
	}

}
