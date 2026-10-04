package com.naukriradar.job.controller;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.jayway.jsonpath.JsonPath;
import com.naukriradar.common.redis.lock.DistributedLock;
import com.naukriradar.common.redis.lock.LockHandle;
import com.naukriradar.common.redis.run.RunLeases;
import com.naukriradar.job.model.FetchRun;
import com.naukriradar.job.model.FetchRunStatus;
import com.naukriradar.job.model.RunTrigger;
import com.naukriradar.job.normalizer.NormalizedJob;
import com.naukriradar.job.repository.FetchRunRepository;
import com.naukriradar.job.repository.JobBatchWriter;
import com.naukriradar.job.repository.JobBatchWriter.FingerprintedJob;
import com.naukriradar.job.repository.JobSourceRepository;
import com.naukriradar.job.service.FetchRunService;
import com.naukriradar.job.service.FingerprintService;
import com.naukriradar.job.support.TestJobs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FetchRunIT {

	@RegisterExtension
	static WireMockExtension board = WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JobSourceRepository sourceRepository;

	@Autowired
	private FetchRunRepository runRepository;

	@Autowired
	private FetchRunService runService;

	@Autowired
	private JobBatchWriter writer;

	@Autowired
	private FingerprintService fingerprints;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private RunLeases leases;

	@Autowired
	private DistributedLock lock;

	/** Only this test's boards may take part in a run. */
	@BeforeEach
	void disableOtherSources() {
		sourceRepository.findAll().forEach(source -> {
			source.setEnabled(false);
			sourceRepository.save(source);
		});
	}

	@Test
	void allSourcesRunInParallelAndOneSlowBoardDoesNotHoldUpTheOthers() throws Exception {
		String tag = UUID.randomUUID().toString().substring(0, 8);
		board.stubFor(WireMock.get(urlPathEqualTo("/fast")).willReturn(okJson(page(
				item("f1", "Java Developer", "Acme " + tag, "Pune"),
				item("f2", "Kafka Engineer", "Acme " + tag, "Pune")))));
		board.stubFor(WireMock.get(urlPathEqualTo("/copycat")).willReturn(okJson(page(
				item("c1", "Java Developer (m/w/d)", "Acme " + tag + " GmbH", "Pune, India"),
				item("c2", "Redis Engineer", "Acme " + tag, "Pune")))));
		board.stubFor(WireMock.get(urlPathEqualTo("/slow"))
				.willReturn(okJson(page()).withFixedDelay(10_000)));
		createSource("fast-" + tag, "/fast", 5);
		createSource("copycat-" + tag, "/copycat", 5);
		createSource("slow-" + tag, "/slow", 1);

		Instant started = Instant.now();
		String runId = JsonPath.read(mvc.perform(post("/api/v1/admin/jobs/fetch-runs"))
				.andExpect(status().isAccepted())
				.andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/v1/admin/jobs/fetch-runs/")))
				.andExpect(jsonPath("$.status").value("RUNNING"))
				.andReturn().getResponse().getContentAsString(), "$.id");

		// only one run at a time
		mvc.perform(post("/api/v1/admin/jobs/fetch-runs")).andExpect(status().isConflict());

		waitUntilFinished(runId);
		Duration took = Duration.between(started, Instant.now());

		mvc.perform(get("/api/v1/admin/jobs/fetch-runs/" + runId))
				.andExpect(jsonPath("$.status").value("PARTIAL"))
				.andExpect(jsonPath("$.sourcesSucceeded").value(2))
				.andExpect(jsonPath("$.sourcesFailed").value(1))
				.andExpect(jsonPath("$.received").value(4))
				.andExpect(jsonPath("$.inserted").value(3))
				.andExpect(jsonPath("$.duplicates").value(1))
				.andExpect(jsonPath("$.sources[?(@.sourceCode == 'slow-" + tag + "')].message")
						.value(hasItem(containsString("timed out"))));
		// the slow board's 10 s delay was cut by its 1 s timeout; nothing waited for it
		assertThat(took).isLessThan(Duration.ofSeconds(8));
		assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jobs WHERE company LIKE ?", Integer.class, "%" + tag + "%"))
				.isEqualTo(3);
	}

	@Test
	void runWithNoEnabledSourcesSucceedsEmpty() throws Exception {
		String runId = JsonPath.read(mvc.perform(post("/api/v1/admin/jobs/fetch-runs"))
				.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString(), "$.id");

		waitUntilFinished(runId);

		mvc.perform(get("/api/v1/admin/jobs/fetch-runs/" + runId))
				.andExpect(jsonPath("$.status").value("SUCCESS"))
				.andExpect(jsonPath("$.message").value("No enabled sources."));
		mvc.perform(get("/api/v1/admin/jobs/fetch-runs").param("limit", "5"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(runId));
	}

	@Test
	void runLeftRunningByACrashIsClosedButLiveAndFreshRunsAreNot() {
		Instant earlier = Instant.now().minus(5, ChronoUnit.MINUTES);
		String live = runService.start(RunTrigger.SCHEDULED, Instant.now());
		// lease first: the background sweep may run at any moment
		leases.begin("fetch", live);
		jdbc.update("UPDATE fetch_runs SET started_at = started_at - INTERVAL 5 MINUTE WHERE id = ?", live);
		String crashed = runService.start(RunTrigger.SCHEDULED, earlier);
		String fresh = runService.start(RunTrigger.SCHEDULED, Instant.now());
		try {
			runService.closeInterruptedRuns();
		}
		finally {
			leases.end("fetch", live);
		}

		FetchRun run = runRepository.findById(crashed).orElseThrow();
		assertThat(run.getStatus()).isEqualTo(FetchRunStatus.FAILED);
		assertThat(run.getMessage()).contains("Interrupted");
		assertThat(runRepository.findById(live).orElseThrow().getStatus()).isEqualTo(FetchRunStatus.RUNNING);
		assertThat(runRepository.findById(fresh).orElseThrow().getStatus()).isEqualTo(FetchRunStatus.RUNNING);
		jdbc.update("UPDATE fetch_runs SET status = 'FAILED' WHERE id IN (?, ?)", live, fresh);
	}

	@Test
	void aRunGoingOnAnotherInstanceBlocksANewOne() throws Exception {
		try (LockHandle otherInstance = lock.tryAcquire("fetch-run").orElseThrow()) {
			mvc.perform(post("/api/v1/admin/jobs/fetch-runs"))
					.andExpect(status().isConflict())
					.andExpect(jsonPath("$.detail").value(containsString("another instance")));
		}
	}

	@Test
	void cleanupClosesStaleJobsAndDeletesOldOnes() throws Exception {
		String source = "clean-" + UUID.randomUUID().toString().substring(0, 8);
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		save(source, now, TestJobs.job("fresh", "Fresh Role", "Co " + source, "Pune", false, now));
		save(source, now.minus(10, ChronoUnit.DAYS), TestJobs.job("stale", "Stale Role", "Co " + source, "Pune", false, null));
		save(source, now.minus(70, ChronoUnit.DAYS), TestJobs.job("ancient", "Ancient Role", "Co " + source, "Pune", false, null));

		mvc.perform(post("/api/v1/admin/jobs/cleanup")).andExpect(status().isOk());

		List<String> remaining = jdbc.queryForList(
				"SELECT CONCAT(external_id, ':', status) FROM jobs WHERE source_code = ? ORDER BY external_id", String.class,
				source);
		assertThat(remaining).containsExactly("fresh:ACTIVE", "stale:CLOSED");
	}

	@Test
	void badRequestsAreRejected() throws Exception {
		mvc.perform(get("/api/v1/admin/jobs/fetch-runs/" + UUID.randomUUID())).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/admin/jobs/fetch-runs").param("limit", "0")).andExpect(status().isBadRequest());
	}

	private void waitUntilFinished(String runId) throws Exception {
		Instant deadline = Instant.now().plusSeconds(30);
		while (Instant.now().isBefore(deadline)) {
			String status = JsonPath.read(mvc.perform(get("/api/v1/admin/jobs/fetch-runs/" + runId))
					.andReturn().getResponse().getContentAsString(), "$.status");
			if (!status.equals("RUNNING")) {
				return;
			}
			Thread.sleep(200);
		}
		throw new AssertionError("Run " + runId + " did not finish");
	}

	private void createSource(String code, String path, int timeoutSeconds) throws Exception {
		mvc.perform(post("/api/v1/admin/job-sources").contentType(MediaType.APPLICATION_JSON).content("""
				{
				  "code": "%s", "name": "%s", "type": "REST_JSON",
				  "baseUrl": "%s", "searchPath": "%s", "method": "GET",
				  "resultsPath": "$.data",
				  "fieldMappings": {"externalId": "$.slug", "title": "$.title", "company": "$.company_name",
				                    "location": "$.location", "applyUrl": "$.url"},
				  "enabled": true, "priority": 1, "timeoutSeconds": %d, "maxPages": 1
				}
				""".formatted(code, code, board.baseUrl(), path, timeoutSeconds)))
				.andExpect(status().isCreated());
	}

	private void save(String source, Instant seenAt, NormalizedJob job) {
		writer.write(source, List.of(new FingerprintedJob(job, fingerprints.fingerprint(job))), seenAt);
	}

	private static String page(String... items) {
		return "{\"data\": [" + String.join(",", items) + "]}";
	}

	private static String item(String slug, String title, String company, String location) {
		return """
				{"slug": "%s", "title": "%s", "company_name": "%s", "location": "%s", "url": "https://jobs.example.com/%s"}
				""".formatted(slug, title, company, location, slug);
	}

}
