package com.naukriradar.job.repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import com.naukriradar.job.model.Job;
import com.naukriradar.job.normalizer.NormalizedJob;
import com.naukriradar.job.repository.JobBatchWriter.FingerprintedJob;
import com.naukriradar.job.repository.JobBatchWriter.WriteCounts;
import com.naukriradar.job.service.FingerprintService;
import com.naukriradar.job.support.TestJobs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JobBatchWriterIT {

	private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.MICROS);

	@Autowired
	private JobBatchWriter writer;

	@Autowired
	private FingerprintService fingerprints;

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void insertsThenUpdatesOnTheNextRun() {
		String source = uniqueSource();
		String id = unique("job");
		NormalizedJob first = TestJobs.job(id, "Java Developer " + id, "Acme", "Pune", false, NOW.minus(2, ChronoUnit.DAYS));

		WriteCounts initial = write(source, first);
		WriteCounts again = write(source, TestJobs.job(id, "Java Developer " + id, "Acme", "Pune", true, null));

		assertThat(initial).isEqualTo(new WriteCounts(1, 0, 0));
		assertThat(again).isEqualTo(new WriteCounts(0, 1, 0));
		Job stored = only(source);
		assertThat(stored.isRemote()).isTrue();
		// the board stopped sending a date, so the first one is kept
		assertThat(stored.getPostedAt()).isEqualTo(NOW.minus(2, ChronoUnit.DAYS));
		assertThat(stored.getVersion()).isEqualTo(1);
	}

	@Test
	void sameJobFromAnotherBoardIsStoredOnce() {
		String title = "Platform Engineer " + unique("t");
		String boardA = uniqueSource();
		String boardB = uniqueSource();

		WriteCounts fromA = write(boardA, TestJobs.job(unique("a"), title + " (m/w/d)", "Globex GmbH", "Berlin", false, NOW));
		WriteCounts fromB = write(boardB, TestJobs.job(unique("b"), title, "Globex", "Berlin, Germany", false, NOW));

		assertThat(fromA.inserted()).isEqualTo(1);
		assertThat(fromB).isEqualTo(new WriteCounts(0, 0, 1));
		assertThat(count(boardA)).isEqualTo(1);
		assertThat(count(boardB)).isZero();
	}

	@Test
	void twoListingsOfTheSameJobOnOneBoardBecomeOneRow() {
		String source = uniqueSource();
		String title = "Data Engineer " + unique("t");

		WriteCounts counts = writeAll(source, List.of(
				TestJobs.job(unique("x"), title, "Initech", "Mumbai", false, NOW),
				TestJobs.job(unique("y"), title, "Initech Pvt Ltd", "Mumbai", false, NOW)));

		assertThat(counts).isEqualTo(new WriteCounts(1, 0, 1));
	}

	@Test
	void anUpdateThatWouldCollideWithAnotherJobLeavesTheRowAlone() {
		String source = uniqueSource();
		String suffix = unique("t");
		String keepId = unique("keep");
		String moveId = unique("move");
		writeAll(source, List.of(
				TestJobs.job(keepId, "SRE " + suffix, "Hooli", "Delhi", false, NOW),
				TestJobs.job(moveId, "QA " + suffix, "Hooli", "Delhi", false, NOW)));

		// the second job is retitled to exactly what the first one is
		WriteCounts counts = write(source, TestJobs.job(moveId, "SRE " + suffix, "Hooli", "Delhi", false, NOW));

		assertThat(counts).isEqualTo(new WriteCounts(0, 0, 1));
		assertThat(jdbc.queryForObject("SELECT title FROM jobs WHERE source_code = ? AND external_id = ?", String.class,
				source, moveId)).isEqualTo("QA " + suffix);
	}

	@Test
	void externalIdsDifferingOnlyInCaseAreTheSameJob() {
		String source = uniqueSource();
		String id = unique("Case");
		write(source, TestJobs.job(id, "Role " + id, "Acme", "Pune", false, NOW));

		WriteCounts counts = write(source, TestJobs.job(id.toUpperCase(), "Role " + id, "Acme", "Pune", false, NOW));

		assertThat(counts).isEqualTo(new WriteCounts(0, 1, 0));
	}

	@Test
	void jobsWithoutAPostingDateSortByWhenWeFoundThem() {
		String source = uniqueSource();
		write(source, TestJobs.job(unique("nodate"), "Role " + unique("t"), "Acme", "Pune", false, null));

		Job stored = only(source);
		assertThat(stored.getPostedAt()).isNull();
		assertThat(stored.getSortAt()).isEqualTo(NOW);
	}

	@Test
	void timesWrittenWithJdbcReadBackTheSameThroughJpa() {
		String source = uniqueSource();
		Instant posted = Instant.parse("2026-09-30T23:30:15.123456Z");
		write(source, TestJobs.job(unique("tz"), "Role " + unique("t"), "Acme", "Pune", false, posted));

		Job stored = only(source);
		assertThat(stored.getPostedAt()).isEqualTo(posted);
		assertThat(stored.getFetchedAt()).isEqualTo(NOW);
	}

	private WriteCounts write(String source, NormalizedJob job) {
		return writeAll(source, List.of(job));
	}

	private WriteCounts writeAll(String source, List<NormalizedJob> jobs) {
		return writer.write(source, jobs.stream().map(j -> new FingerprintedJob(j, fingerprints.fingerprint(j))).toList(), NOW);
	}

	private Job only(String source) {
		List<String> ids = jdbc.queryForList("SELECT id FROM jobs WHERE source_code = ?", String.class, source);
		assertThat(ids).hasSize(1);
		return jobRepository.findById(ids.get(0)).orElseThrow();
	}

	private int count(String source) {
		return jdbc.queryForObject("SELECT COUNT(*) FROM jobs WHERE source_code = ?", Integer.class, source);
	}

	private static String uniqueSource() {
		return unique("s");
	}

	private static String unique(String prefix) {
		return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
	}

}
