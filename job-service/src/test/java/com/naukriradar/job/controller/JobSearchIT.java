package com.naukriradar.job.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.naukriradar.job.normalizer.NormalizedJob;
import com.naukriradar.job.repository.JobBatchWriter;
import com.naukriradar.job.repository.JobBatchWriter.FingerprintedJob;
import com.naukriradar.job.service.FingerprintService;
import com.naukriradar.job.support.TestJobs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Every search runs with source=<this test's source> so other tests' jobs don't interfere. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobSearchIT {

	private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JobBatchWriter writer;

	@Autowired
	private FingerprintService fingerprints;

	@Autowired
	private JdbcTemplate jdbc;

	private String source;

	@BeforeEach
	void seed() {
		source = "s-" + UUID.randomUUID().toString().substring(0, 8);
		save(
				TestJobs.job("j1", "Java Backend Developer", "Acme", "Pune, India", false, daysAgo(1)),
				TestJobs.job("j2", "JavaScript Developer", "Globex", "Bengaluru", false, daysAgo(2)),
				TestJobs.job("j3", "Senior Java Engineer", "Initech", "Remote", true, daysAgo(3)),
				TestJobs.job("j4", "Go Developer", "Hooli", "Pune", false, daysAgo(4)),
				TestJobs.job("j5", "C# .NET Developer", "Umbrella", "Hyderabad", false, daysAgo(20)),
				TestJobs.job("j6", "Golang Engineer", "Stark", "Berlin", true, daysAgo(40)),
				TestJobs.job("j7", "100% Remote QA_Lead", "Wayne", "Remote", true, null));
	}

	@Test
	void wordSearchMatchesWholeWordsOnly() throws Exception {
		search(Map.of("q", "java"))
				.andExpect(jsonPath("$.items[*].title", contains("Java Backend Developer", "Senior Java Engineer")));
	}

	@Test
	void everyWordMustMatch() throws Exception {
		search(Map.of("q", "java senior")).andExpect(jsonPath("$.items[*].title", contains("Senior Java Engineer")));
	}

	@Test
	void shortAndSymbolTermsStillWork() throws Exception {
		search(Map.of("q", "go")).andExpect(jsonPath("$.items[*].title", contains("Go Developer")));
		search(Map.of("q", "c#")).andExpect(jsonPath("$.items[*].title", contains("C# .NET Developer")));
		search(Map.of("q", ".net")).andExpect(jsonPath("$.items[*].title", contains("C# .NET Developer")));
	}

	@Test
	void hostileInputIsJustText() throws Exception {
		search(Map.of("q", "java\" OR 1=1 -- +(*) ~@<>")).andExpect(status().isOk());
		search(Map.of("location", "%")).andExpect(jsonPath("$.items", hasSize(0)));
		search(Map.of("q", "QA_Lead")).andExpect(jsonPath("$.items[*].title", contains("100% Remote QA_Lead")));
	}

	@Test
	void filtersCombine() throws Exception {
		search(Map.of("location", "pune")).andExpect(jsonPath("$.items[*].title", contains("Java Backend Developer", "Go Developer")));
		search(Map.of("remote", "true")).andExpect(jsonPath("$.items[*].title",
				containsInAnyOrder("Senior Java Engineer", "Golang Engineer", "100% Remote QA_Lead")));
		search(Map.of("postedWithinDays", "7", "remote", "false")).andExpect(jsonPath("$.items[*].title",
				contains("Java Backend Developer", "JavaScript Developer", "Go Developer")));
	}

	@Test
	void keysetPagingVisitsEveryJobOnceNewestFirst() throws Exception {
		List<String> seen = new ArrayList<>();
		String cursor = null;
		int pages = 0;
		do {
			Map<String, String> params = cursor == null ? Map.of("limit", "3") : Map.of("limit", "3", "cursor", cursor);
			String body = search(params).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
			seen.addAll(JsonPath.read(body, "$.items[*].title"));
			cursor = JsonPath.read(body, "$.nextCursor");
			pages++;
		}
		while (cursor != null && pages < 10);

		// the undated job sorts by when it was found, which is now, so it comes first
		assertThat(seen).containsExactly("100% Remote QA_Lead", "Java Backend Developer", "JavaScript Developer",
				"Senior Java Engineer", "Go Developer", "C# .NET Developer", "Golang Engineer");
		assertThat(pages).isEqualTo(3);
	}

	@Test
	void closedJobsAreNotListed() throws Exception {
		jdbc.update("UPDATE jobs SET status = 'CLOSED' WHERE source_code = ? AND external_id = 'j1'", source);

		search(Map.of("q", "java")).andExpect(jsonPath("$.items[*].title", contains("Senior Java Engineer")));
	}

	@Test
	void badParametersAreRejected() throws Exception {
		mvc.perform(get("/api/v1/jobs").param("cursor", "garbage!"))
				.andExpect(status().isBadRequest())
				.andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
		mvc.perform(get("/api/v1/jobs").param("limit", "0")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/jobs").param("limit", "500")).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/jobs").param("postedWithinDays", "-1")).andExpect(status().isBadRequest());
	}

	@Test
	void detailIncludesTheDescription() throws Exception {
		String body = search(Map.of("q", "golang")).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.items[0].id");

		mvc.perform(get("/api/v1/jobs/" + id))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.externalId").value("j6"))
				.andExpect(jsonPath("$.description").value(startsWith("Golang Engineer at Stark")))
				.andExpect(jsonPath("$.status").value("ACTIVE"));
		mvc.perform(get("/api/v1/jobs/" + UUID.randomUUID())).andExpect(status().isNotFound());
	}

	@Test
	void detailIsCachedUntilAFetchCleanupOrAdminClearsIt() throws Exception {
		String body = search(Map.of("q", "golang")).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.items[0].id");
		mvc.perform(get("/api/v1/jobs/" + id)).andExpect(jsonPath("$.title").value("Golang Engineer"));

		jdbc.update("UPDATE jobs SET title = 'Go Engineer' WHERE id = ?", id);
		mvc.perform(get("/api/v1/jobs/" + id)).andExpect(jsonPath("$.title").value("Golang Engineer"));

		mvc.perform(delete("/api/v1/admin/cache/job-detail")).andExpect(status().isNoContent());
		mvc.perform(get("/api/v1/jobs/" + id)).andExpect(jsonPath("$.title").value("Go Engineer"));

		jdbc.update("UPDATE jobs SET title = 'Golang Engineer' WHERE id = ?", id);
		mvc.perform(post("/api/v1/admin/jobs/cleanup")).andExpect(status().isOk());
		mvc.perform(get("/api/v1/jobs/" + id)).andExpect(jsonPath("$.title").value("Golang Engineer"));
	}

	@Test
	void cacheStatsAreListedAndUnknownCachesAreNotFound() throws Exception {
		String body = search(Map.of("q", "golang")).andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(body, "$.items[0].id");
		mvc.perform(get("/api/v1/jobs/" + id));
		mvc.perform(get("/api/v1/jobs/" + id));

		mvc.perform(get("/api/v1/admin/cache"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("job-detail"))
				.andExpect(jsonPath("$[0].localHits").value(greaterThanOrEqualTo(1)))
				.andExpect(jsonPath("$[0].localTtl").exists());
		mvc.perform(delete("/api/v1/admin/cache/nope")).andExpect(status().isNotFound());
	}

	/**
	 * TREE format reads the same on MySQL 8.4 and newer. The page query forces the index only
	 * because the test table is tiny and the optimizer would rather scan it; what matters is
	 * that the index can serve the ORDER BY without sorting.
	 */
	@Test
	void queriesUseTheIndexes() {
		String fullText = jdbc.queryForObject("EXPLAIN FORMAT=TREE SELECT id FROM jobs WHERE status = 'ACTIVE'"
				+ " AND MATCH(title, company, description) AGAINST ('+java' IN BOOLEAN MODE)", String.class);
		String page = jdbc.queryForObject("EXPLAIN FORMAT=TREE SELECT id FROM jobs FORCE INDEX (idx_jobs_status_sort)"
				+ " WHERE status = 'ACTIVE' ORDER BY sort_at DESC, id DESC LIMIT 20", String.class);

		assertThat(fullText).contains("Full-text index search");
		assertThat(page).contains("idx_jobs_status_sort").doesNotContain("Sort:");
	}

	private ResultActions search(Map<String, String> params) throws Exception {
		var request = get("/api/v1/jobs").param("source", source);
		params.forEach(request::param);
		return mvc.perform(request).andExpect(status().isOk());
	}

	/** Company names get this test's source appended: fingerprints are unique across the table. */
	private void save(NormalizedJob... jobs) {
		List<FingerprintedJob> unique = List.of(jobs).stream()
				.map(j -> new NormalizedJob(j.externalId(), j.title(), j.company() + " " + source, j.location(), j.remote(),
						j.salaryMin(), j.salaryMax(), j.currency(), j.postedAt(), j.applyUrl(), j.description()))
				.map(j -> new FingerprintedJob(j, fingerprints.fingerprint(j)))
				.toList();
		writer.write(source, unique, NOW);
	}

	private static Instant daysAgo(int days) {
		return NOW.minus(days, ChronoUnit.DAYS);
	}

}
