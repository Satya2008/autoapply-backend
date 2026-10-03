package com.naukriradar.core.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full stack against MySQL; WireMock on port 18083 plays matching-service. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationControllerIT {

	private static final String USER_HEADER = "X-User-Id";

	private static final String APPS = "/api/v1/me/applications";

	@RegisterExtension
	static WireMockExtension matching = WireMockExtension.newInstance().options(wireMockConfig().port(18083)).build();

	@Autowired
	private MockMvc mvc;

	@Test
	void runRoutesEachMatchByRiskAndSimulatesTheLowRiskOnes() throws Exception {
		String user = userWithProfile(true, 10);
		stubMatches(user, match("gh", 90, "https://boards.greenhouse.io/acme/jobs/1"),
				match("li", 85, "https://www.linkedin.com/jobs/view/2"),
				match("unknown", 80, "https://careers.example.org/jobs/3"),
				match("weak", 40, "https://jobs.lever.co/acme/4"));

		String runId = JsonPath.read(mvc.perform(post(APPS + "/runs").header(USER_HEADER, user))
				.andExpect(status().isAccepted())
				.andExpect(header().string(HttpHeaders.LOCATION, startsWith(APPS + "/runs/")))
				.andReturn().getResponse().getContentAsString(), "$.id");
		waitForRun(user, runId)
				.andExpect(jsonPath("$.status").value("SUCCESS"))
				.andExpect(jsonPath("$.matchesConsidered").value(4))
				.andExpect(jsonPath("$.queued").value(1))
				.andExpect(jsonPath("$.simulated").value(1))
				.andExpect(jsonPath("$.needsYou").value(2))
				.andExpect(jsonPath("$.belowScore").value(1));

		mvc.perform(get(APPS + "/needs-you").header(USER_HEADER, user))
				.andExpect(jsonPath("$[*].application.jobId", contains("li", "unknown")))
				.andExpect(jsonPath("$[0].reason").value("linkedin.com doesn't allow automated applications."))
				.andExpect(jsonPath("$[0].prefill.email").value(startsWith("apply-")))
				.andExpect(jsonPath("$[0].prefill.expectedSalary").value("₹12,00,000 per year"))
				.andExpect(jsonPath("$[0].application.riskBand").value("HIGH"));

		String gh = appId(user, "gh");
		mvc.perform(get(APPS + "/" + gh).header(USER_HEADER, user))
				.andExpect(jsonPath("$.application.status").value("SIMULATED"))
				.andExpect(jsonPath("$.application.submittedVia").value("SIMULATED"))
				.andExpect(jsonPath("$.timeline[*].toStatus", contains("PLANNED", "QUEUED", "SIMULATED")))
				.andExpect(jsonPath("$.timeline[2].note").value("Simulated: nothing was sent to the employer."));
		mvc.perform(get(APPS + "/" + appId(user, "li")).header(USER_HEADER, user))
				.andExpect(jsonPath("$.timeline[*].toStatus", not(hasItem("QUEUED"))));
	}

	@Test
	void runningAgainDoesNotApplyTwice() throws Exception {
		String user = userWithProfile(true, 10);
		stubMatches(user, match("a", 90, "https://boards.greenhouse.io/x/1"), match("b", 80, "https://www.naukri.com/2"));
		runToEnd(user);

		runToEnd(user)
				.andExpect(jsonPath("$.alreadyApplied").value(2))
				.andExpect(jsonPath("$.queued").value(0))
				.andExpect(jsonPath("$.needsYou").value(0));
		mvc.perform(get(APPS).header(USER_HEADER, user)).andExpect(jsonPath("$.items", hasSize(2)));
	}

	@Test
	void dailyLimitHandsTheRestToTheUser() throws Exception {
		String user = userWithProfile(true, 1);
		stubMatches(user, match("first", 90, "https://boards.greenhouse.io/x/1"),
				match("second", 85, "https://jobs.lever.co/x/2"));

		runToEnd(user).andExpect(jsonPath("$.queued").value(1)).andExpect(jsonPath("$.needsYou").value(1));

		mvc.perform(get(APPS + "/needs-you").header(USER_HEADER, user))
				.andExpect(jsonPath("$[0].application.jobId").value("second"))
				.andExpect(jsonPath("$[0].reason").value("Today's limit of 1 automatic applications is reached."));
	}

	@Test
	void withAutoApplyOffNothingIsAutomated() throws Exception {
		String user = userWithProfile(false, 10);
		stubMatches(user, match("a", 90, "https://boards.greenhouse.io/x/1"), match("b", 80, "https://jobs.lever.co/x/2"));

		runToEnd(user).andExpect(jsonPath("$.queued").value(0)).andExpect(jsonPath("$.needsYou").value(2));
		mvc.perform(get(APPS + "/needs-you").header(USER_HEADER, user))
				.andExpect(jsonPath("$[*].reason", contains("Auto apply is off, so this is ready for you to send.",
						"Auto apply is off, so this is ready for you to send.")));
	}

	@Test
	void doneAndSkipCanBeRepeatedAndStatusesFollowTheRules() throws Exception {
		String user = userWithProfile(true, 10);
		stubMatches(user, match("x", 90, "https://www.linkedin.com/jobs/1"), match("y", 80, "https://www.indeed.com/2"));
		runToEnd(user);
		String x = appId(user, "x");
		String y = appId(user, "y");

		mvc.perform(post(APPS + "/" + x + "/done").header(USER_HEADER, user)).andExpect(jsonPath("$.application.status").value("APPLIED"));
		mvc.perform(post(APPS + "/" + x + "/done").header(USER_HEADER, user))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.application.status").value("APPLIED"))
				.andExpect(jsonPath("$.application.submittedVia").value("MANUAL"))
				.andExpect(jsonPath("$.timeline[?(@.toStatus == 'APPLIED')]", hasSize(1)));

		report(user, x, "INTERVIEW").andExpect(status().isOk());
		report(user, x, "INTERVIEW").andExpect(status().isOk());
		report(user, x, "OFFER").andExpect(jsonPath("$.application.status").value("OFFER"));
		report(user, x, "REJECTED").andExpect(status().isConflict());
		mvc.perform(post(APPS + "/" + x + "/skip").header(USER_HEADER, user)).andExpect(status().isConflict());

		mvc.perform(post(APPS + "/" + y + "/skip").header(USER_HEADER, user)).andExpect(jsonPath("$.application.status").value("SKIPPED"));
		mvc.perform(post(APPS + "/" + y + "/skip").header(USER_HEADER, user)).andExpect(status().isOk());
		mvc.perform(post(APPS + "/" + y + "/done").header(USER_HEADER, user)).andExpect(status().isConflict());
		report(user, y, "OFFER").andExpect(status().isConflict());
		report(user, y, "APPLIED").andExpect(status().isBadRequest());

		mvc.perform(get(APPS + "/stats").header(USER_HEADER, user))
				.andExpect(jsonPath("$.total").value(2))
				.andExpect(jsonPath("$.byStatus.OFFER").value(1))
				.andExpect(jsonPath("$.byStatus.SKIPPED").value(1))
				.andExpect(jsonPath("$.byStatus.QUEUED").value(0))
				.andExpect(jsonPath("$.sent").value(1))
				.andExpect(jsonPath("$.dailyLimit").value(10));
	}

	@Test
	void listFiltersAndPagesNewestFirst() throws Exception {
		String user = userWithProfile(false, 10);
		stubMatches(user, match("a", 90, "https://x.example.org/1"), match("b", 85, "https://x.example.org/2"),
				match("c", 80, "https://x.example.org/3"));
		runToEnd(user);
		mvc.perform(post(APPS + "/" + appId(user, "b") + "/skip").header(USER_HEADER, user));

		mvc.perform(get(APPS).header(USER_HEADER, user).param("status", "NEEDS_YOU"))
				.andExpect(jsonPath("$.items[*].jobId", containsInAnyOrder("a", "c")));

		List<String> seen = new ArrayList<>();
		String cursor = null;
		do {
			var request = get(APPS).header(USER_HEADER, user).param("limit", "1");
			if (cursor != null) {
				request.param("cursor", cursor);
			}
			String body = mvc.perform(request).andReturn().getResponse().getContentAsString();
			seen.addAll(JsonPath.read(body, "$.items[*].jobId"));
			cursor = JsonPath.read(body, "$.nextCursor");
		}
		while (cursor != null);
		assertThat(seen).containsExactlyInAnyOrder("a", "b", "c");

		mvc.perform(get(APPS).header(USER_HEADER, user).param("cursor", "nope")).andExpect(status().isBadRequest());
		mvc.perform(get(APPS).header(USER_HEADER, user).param("status", "NOT_A_STATUS")).andExpect(status().isBadRequest());
	}

	@Test
	void adminPortalOverrideCanMakeASiteSafeToAutomate() throws Exception {
		String domain = "careers-" + UUID.randomUUID().toString().substring(0, 8) + ".example.com";
		String portalId = JsonPath.read(mvc.perform(post("/api/v1/admin/portals").contentType(MediaType.APPLICATION_JSON)
				.content("{\"domain\": \"https://www." + domain + "/jobs\", \"name\": \"Acme careers\", \"riskBand\": \"LOW\","
						+ " \"enabled\": true, \"selectors\": {\"email\": \"#email\"}}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.domain").value(domain))
				.andReturn().getResponse().getContentAsString(), "$.id");
		String user = userWithProfile(true, 10);
		stubMatches(user, match("own", 90, "https://" + domain + "/apply/1"));

		runToEnd(user).andExpect(jsonPath("$.queued").value(1));
		mvc.perform(get(APPS + "/" + appId(user, "own")).header(USER_HEADER, user))
				.andExpect(jsonPath("$.riskReason").value("Acme careers is set to LOW risk by an admin."));

		mvc.perform(post("/api/v1/admin/portals").contentType(MediaType.APPLICATION_JSON)
				.content("{\"domain\": \"" + domain + "\", \"name\": \"Again\", \"riskBand\": \"HIGH\", \"enabled\": true}"))
				.andExpect(status().isConflict());
		mvc.perform(post("/api/v1/admin/portals").contentType(MediaType.APPLICATION_JSON)
				.content("{\"domain\": \"localhost\", \"name\": \"Bad\", \"riskBand\": \"HIGH\", \"enabled\": true}"))
				.andExpect(status().isBadRequest());
		mvc.perform(delete("/api/v1/admin/portals/" + portalId)).andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/admin/portals/" + portalId)).andExpect(status().isNotFound());
	}

	@Test
	void matchingServiceOutageFailsTheRunAndFreesTheUser() throws Exception {
		String user = userWithProfile(true, 10);
		matching.stubFor(WireMock.get(urlPathEqualTo("/internal/v1/users/" + user + "/matches"))
				.willReturn(aResponse().withStatus(503)));

		runToEnd(user)
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.message").value(startsWith("matching-service is unavailable")));

		stubMatches(user);
		runToEnd(user).andExpect(jsonPath("$.status").value("SUCCESS"));
	}

	@Test
	void secondRunWhileOneIsGoingIsRefused() throws Exception {
		String user = userWithProfile(true, 10);
		matching.stubFor(WireMock.get(urlPathEqualTo("/internal/v1/users/" + user + "/matches"))
				.willReturn(okJson("[]").withFixedDelay(2_000)));

		String runId = JsonPath.read(mvc.perform(post(APPS + "/runs").header(USER_HEADER, user))
				.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.id");
		mvc.perform(post(APPS + "/runs").header(USER_HEADER, user)).andExpect(status().isConflict());
		waitForRun(user, runId).andExpect(jsonPath("$.status").value("SUCCESS"));
	}

	@Test
	void applicationsArePrivate() throws Exception {
		String owner = userWithProfile(false, 10);
		stubMatches(owner, match("mine", 90, "https://x.example.org/1"));
		runToEnd(owner);
		String id = appId(owner, "mine");

		String stranger = userWithProfile(false, 10);
		mvc.perform(get(APPS + "/" + id).header(USER_HEADER, stranger)).andExpect(status().isNotFound());
		mvc.perform(post(APPS + "/" + id + "/done").header(USER_HEADER, stranger)).andExpect(status().isNotFound());
		mvc.perform(get(APPS).header(USER_HEADER, stranger)).andExpect(jsonPath("$.items", hasSize(0)));
		mvc.perform(get(APPS)).andExpect(status().isUnauthorized());
	}

	private String userWithProfile(boolean autoApply, int dailyLimit) throws Exception {
		String body = mvc.perform(post("/api/v1/dev/users").contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\": \"apply-" + UUID.randomUUID() + "@example.com\"}"))
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
		String user = JsonPath.read(body, "$.id");
		mvc.perform(put("/api/v1/me/skills").header(USER_HEADER, user).contentType(MediaType.APPLICATION_JSON)
				.content("{\"skills\": [{\"name\": \"Java\"}, {\"name\": \"SQL\"}, {\"name\": \"Docker\"}]}"))
				.andExpect(status().isOk());
		mvc.perform(put("/api/v1/me/profile").header(USER_HEADER, user).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"fullName": "Satya", "expectedSalary": 1200000, "targetRoles": ["Backend Engineer"],
						 "remoteOk": true, "minMatchScore": 50, "dailyApplyLimit": %d, "autoApplyEnabled": %s}
						""".formatted(dailyLimit, autoApply)))
				.andExpect(status().isOk());
		return user;
	}

	private static void stubMatches(String user, String... matches) {
		matching.stubFor(WireMock.get(urlPathEqualTo("/internal/v1/users/" + user + "/matches"))
				.willReturn(okJson("[" + String.join(",", matches) + "]")));
	}

	private static String match(String jobId, int score, String applyUrl) {
		return """
				{"jobId": "%s", "score": %d, "title": "Role %s", "company": "Company %s", "location": "Pune", "applyUrl": "%s"}
				""".formatted(jobId, score, jobId, jobId, applyUrl);
	}

	private ResultActions report(String user, String id, String status) throws Exception {
		return mvc.perform(patch(APPS + "/" + id + "/status").header(USER_HEADER, user)
				.contentType(MediaType.APPLICATION_JSON).content("{\"status\": \"" + status + "\"}"));
	}

	private String appId(String user, String jobId) throws Exception {
		String body = mvc.perform(get(APPS).header(USER_HEADER, user).param("limit", "100"))
				.andReturn().getResponse().getContentAsString();
		List<String> ids = JsonPath.read(body, "$.items[?(@.jobId == '" + jobId + "')].id");
		assertThat(ids).hasSize(1);
		return ids.get(0);
	}

	private ResultActions runToEnd(String user) throws Exception {
		String runId = JsonPath.read(mvc.perform(post(APPS + "/runs").header(USER_HEADER, user))
				.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.id");
		return waitForRun(user, runId);
	}

	private ResultActions waitForRun(String user, String runId) throws Exception {
		Instant deadline = Instant.now().plus(20, ChronoUnit.SECONDS);
		while (Instant.now().isBefore(deadline)) {
			ResultActions result = mvc.perform(get(APPS + "/runs/" + runId).header(USER_HEADER, user));
			if (!JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.status").equals("RUNNING")) {
				return result;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("Run " + runId + " didn't finish");
	}

}
