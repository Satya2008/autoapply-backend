package com.naukriradar.job.controller;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.jayway.jsonpath.JsonPath;
import com.naukriradar.job.normalizer.NormalizedJob;
import com.naukriradar.job.repository.JobBatchWriter;
import com.naukriradar.job.repository.JobBatchWriter.FingerprintedJob;
import com.naukriradar.job.service.FingerprintService;
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
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InternalJobControllerIT {

	private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

	@Autowired
	private MockMvc mvc;

	@Autowired
	private JobBatchWriter writer;

	@Autowired
	private FingerprintService fingerprints;

	@Autowired
	private JdbcTemplate jdbc;

	/** A made-up word only this test's jobs contain, so other tests' jobs never match. */
	private String token;

	private String source;

	@BeforeEach
	void seed() {
		token = "qk" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
		source = "s-" + token;
		save(job("strong", token + " Engineer", token + " in title and " + token + " in the text", NOW.minus(1, ChronoUnit.DAYS)));
		save(job("weak", "Backend Engineer", "Mentions " + token + " once", NOW.minus(2, ChronoUnit.DAYS)));
		save(job("old", token + " Lead", token + " " + token, NOW.minus(100, ChronoUnit.DAYS)));
		save(job("closed", token + " Analyst", token, NOW));
		jdbc.update("UPDATE jobs SET status = 'CLOSED' WHERE source_code = ? AND external_id = 'closed'", source);
	}

	@Test
	void ranksByRelevanceAndSkipsOldAndClosedJobs() throws Exception {
		candidates("{\"keywords\": [\"" + token + "\"], \"postedWithinDays\": 30}")
				.andExpect(jsonPath("$[*].title", contains(token + " Engineer", "Backend Engineer")))
				.andExpect(jsonPath("$[0].description", startsWith(token + " in title")))
				.andExpect(jsonPath("$[0].postedAt").exists());
	}

	@Test
	void symbolsAndShortWordsInKeywordsAreHarmless() throws Exception {
		candidates("{\"keywords\": [\"c#\", \"go\", \"+" + token + "*\", \"\\\"drop\\\"\"], \"postedWithinDays\": 30}")
				.andExpect(jsonPath("$[*].title", contains(token + " Engineer", "Backend Engineer")));
	}

	@Test
	void noKeywordsGivesTheNewestJobs() throws Exception {
		candidates("{\"keywords\": [], \"limit\": 5}").andExpect(jsonPath("$", hasSize(lessThanOrEqualTo(5))));
	}

	@Test
	void descriptionsAreCut() throws Exception {
		save(job("long", token + " Writer", token + " " + "x".repeat(20_000), NOW));

		String body = candidates("{\"keywords\": [\"" + token + "\"]}").andReturn().getResponse().getContentAsString();
		List<String> descriptions = JsonPath.read(body, "$[?(@.title == '" + token + " Writer')].description");

		assertThat(descriptions).singleElement().satisfies(d -> assertThat(d).hasSize(5000));
	}

	@Test
	void badQueriesAreRejected() throws Exception {
		mvc.perform(post("/internal/v1/jobs/candidates").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/internal/v1/jobs/candidates").contentType(MediaType.APPLICATION_JSON)
				.content("{\"keywords\": [], \"limit\": 5000}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void recentJobsComePageByPageInIdOrderWithoutOldOrClosedOnes() throws Exception {
		List<String> ours = jdbc.queryForList("SELECT id FROM jobs WHERE source_code = ? AND status = 'ACTIVE'"
				+ " AND sort_at >= NOW() - INTERVAL 30 DAY ORDER BY id", String.class, source);
		assertThat(ours).hasSize(2);
		String before = jdbc.queryForObject("SELECT MAX(id) FROM jobs WHERE id < ?", String.class, ours.getFirst());

		String first = mvc.perform(get("/internal/v1/jobs/recent").param("days", "30").param("limit", "1")
				.param("after", before == null ? "" : before))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<String>>read(first, "$[*].id")).containsExactly(ours.get(0));
		String second = mvc.perform(get("/internal/v1/jobs/recent").param("days", "30").param("limit", "1")
				.param("after", ours.get(0))).andReturn().getResponse().getContentAsString();
		assertThat(JsonPath.<List<String>>read(second, "$[*].id")).containsExactly(ours.get(1));
		mvc.perform(get("/internal/v1/jobs/recent").param("limit", "5000")).andExpect(status().isBadRequest());
	}

	@Test
	void jobsByIdComeInTheAskedOrderAndOnlyWhileActive() throws Exception {
		String strong = idOf("strong");
		String weak = idOf("weak");
		String closed = idOf("closed");

		mvc.perform(post("/internal/v1/jobs/by-ids").contentType(MediaType.APPLICATION_JSON)
				.content("{\"ids\": [\"" + weak + "\", \"" + closed + "\", \"nope\", \"" + strong + "\", \"" + weak + "\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].id", contains(weak, strong)));
		mvc.perform(post("/internal/v1/jobs/by-ids").contentType(MediaType.APPLICATION_JSON).content("{\"ids\": []}"))
				.andExpect(jsonPath("$", hasSize(0)));

		// one job by id, even closed: a cover letter can be written after the posting closed
		mvc.perform(get("/internal/v1/jobs/" + closed)).andExpect(status().isOk())
				.andExpect(jsonPath("$.title").value(token + " Analyst"));
		mvc.perform(get("/internal/v1/jobs/nope")).andExpect(status().isNotFound());
	}

	private String idOf(String externalId) {
		return jdbc.queryForObject("SELECT id FROM jobs WHERE source_code = ? AND external_id = ?", String.class, source,
				externalId);
	}

	private ResultActions candidates(String body) throws Exception {
		return mvc.perform(post("/internal/v1/jobs/candidates").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk());
	}

	private NormalizedJob job(String id, String title, String description, Instant postedAt) {
		return new NormalizedJob(id, title, "Company " + token, "Pune", false, null, null, null, postedAt,
				"https://jobs.example.com/" + id, description);
	}

	private void save(NormalizedJob job) {
		writer.write(source, List.of(new FingerprintedJob(job, fingerprints.fingerprint(job))), NOW);
	}

}
