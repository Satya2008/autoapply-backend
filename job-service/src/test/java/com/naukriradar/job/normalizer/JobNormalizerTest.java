package com.naukriradar.job.normalizer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.naukriradar.job.model.JobField;
import com.naukriradar.job.provider.RawJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class JobNormalizerTest {

	private static final Instant NOW = Instant.parse("2026-10-03T12:00:00Z");

	private final JobNormalizer normalizer = new JobNormalizer();

	@Test
	void cleansAValidJob() {
		NormalizedJob job = ok(Map.of(
				JobField.EXTERNAL_ID, 42,
				JobField.TITLE, "  Java   <b>Developer</b> ",
				JobField.COMPANY, "Acme &amp; Co",
				JobField.APPLY_URL, "https://jobs.example.com/42",
				JobField.LOCATION, "Pune",
				JobField.DESCRIPTION, "<p>Spring</p><ul><li>Java</li><li>SQL</li></ul><script>alert(1)</script>",
				JobField.CURRENCY, "inr"));

		assertThat(job.externalId()).isEqualTo("42");
		assertThat(job.title()).isEqualTo("Java Developer");
		assertThat(job.company()).isEqualTo("Acme & Co");
		assertThat(job.description()).isEqualTo("Spring\n\n- Java\n- SQL").doesNotContain("alert");
		assertThat(job.currency()).isEqualTo("INR");
		assertThat(job.remote()).isFalse();
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			EXTERNAL_ID | missing externalId
			TITLE       | missing title
			COMPANY     | missing company
			APPLY_URL   | applyUrl
			""")
	void skipsJobsWithoutRequiredFields(JobField missing, String reason) {
		Map<JobField, Object> values = new EnumMap<>(validJob());
		values.remove(missing);

		NormalizationResult result = normalizer.normalize(new RawJob(values), NOW);

		assertThat(result.isOk()).isFalse();
		assertThat(result.skipReason()).contains(reason);
	}

	@ParameterizedTest
	@ValueSource(strings = { "javascript:alert(1)", "/relative/path", "ftp://files.example.com/x", "not a url", "   " })
	void skipsJobsWithUnusableApplyLinks(String url) {
		Map<JobField, Object> values = new EnumMap<>(validJob());
		values.put(JobField.APPLY_URL, url);

		assertThat(normalizer.normalize(new RawJob(values), NOW).isOk()).isFalse();
	}

	@Test
	void blankStringsCountAsMissing() {
		Map<JobField, Object> values = new EnumMap<>(validJob());
		values.put(JobField.TITLE, "   ");

		assertThat(normalizer.normalize(new RawJob(values), NOW).isOk()).isFalse();
	}

	@Test
	void longTextIsTruncated() {
		Map<JobField, Object> values = new EnumMap<>(validJob());
		values.put(JobField.TITLE, "x".repeat(1000));
		values.put(JobField.DESCRIPTION, "y".repeat(50_000));

		NormalizedJob job = ok(values);

		assertThat(job.title()).hasSize(JobNormalizer.MAX_TITLE);
		assertThat(job.description()).hasSize(JobNormalizer.MAX_DESCRIPTION);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "NULL", textBlock = """
			true         | NULL                 | true
			yes          | NULL                 | true
			Remote       | NULL                 | true
			false        | Remote               | false
			NULL         | Remote               | true
			NULL         | Bengaluru (WFH)      | true
			NULL         | Bengaluru            | false
			""")
	void remoteFlagFromTheBoardOrFromTheLocation(String flag, String location, boolean expected) {
		Map<JobField, Object> values = new EnumMap<>(validJob());
		if (flag != null) {
			values.put(JobField.REMOTE, flag.equals("true") || flag.equals("false") ? Boolean.valueOf(flag) : flag);
		}
		if (location != null) {
			values.put(JobField.LOCATION, location);
		}

		assertThat(ok(values).remote()).isEqualTo(expected);
	}

	@Test
	void locationListsAreJoined() {
		Map<JobField, Object> values = new EnumMap<>(validJob());
		values.put(JobField.LOCATION, List.of("Pune", " Mumbai ", "Pune", ""));

		assertThat(ok(values).location()).isEqualTo("Pune, Mumbai");
	}

	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', nullValues = "NULL", textBlock = """
			1790000000                 | 2026-09-21T14:13:20Z
			1790000000000              | 2026-09-21T14:13:20Z
			2026-09-30T10:15:30Z       | 2026-09-30T10:15:30Z
			2026-09-30T10:15:30+05:30  | 2026-09-30T04:45:30Z
			2026-09-30T10:15:30        | 2026-09-30T10:15:30Z
			2026-09-30 10:15:30        | 2026-09-30T10:15:30Z
			2026-09-30                 | 2026-09-30T00:00:00Z
			Wed, 30 Sep 2026 10:15:30 GMT | 2026-09-30T10:15:30Z
			3 days ago                 | 2026-09-30T12:00:00Z
			2 weeks ago                | 2026-09-19T12:00:00Z
			yesterday                  | 2026-10-02T12:00:00Z
			30+ days ago               | 2026-09-03T12:00:00Z
			""")
	void readsManyDateFormats(String raw, String expected) {
		Map<JobField, Object> values = new EnumMap<>(validJob());
		values.put(JobField.POSTED_AT, raw.matches("\\d+") ? Long.valueOf(raw) : raw);

		assertThat(ok(values).postedAt()).isEqualTo(Instant.parse(expected));
	}

	@ParameterizedTest
	@ValueSource(strings = { "next tuesday", "31/31/2026", "0", "1999-12-31", "2030-01-01" })
	void unusableDatesBecomeNull(String raw) {
		Map<JobField, Object> values = new EnumMap<>(validJob());
		values.put(JobField.POSTED_AT, raw);

		assertThat(ok(values).postedAt()).isNull();
	}

	@Test
	void slightlyFutureDatesFromClockSkewAreKept() {
		Map<JobField, Object> values = new EnumMap<>(validJob());
		values.put(JobField.POSTED_AT, NOW.plus(2, ChronoUnit.HOURS).toString());

		assertThat(ok(values).postedAt()).isEqualTo(NOW.plus(2, ChronoUnit.HOURS));
	}

	@ParameterizedTest(name = "min={0} max={1}")
	@CsvSource(delimiter = '|', nullValues = "NULL", textBlock = """
			85000        | NULL      | 85000   | NULL
			50,000       | 70,000    | 50000   | 70000
			60k - 80k    | NULL      | 60000   | 80000
			12 LPA       | NULL      | 1200000 | NULL
			8-12 lakh    | NULL      | 800000  | 1200000
			90000        | 70000     | 70000   | 90000
			competitive  | NULL      | NULL    | NULL
			""")
	void readsSalaries(String min, String max, Long expectedMin, Long expectedMax) {
		Map<JobField, Object> values = new EnumMap<>(validJob());
		if (min != null) {
			values.put(JobField.SALARY_MIN, min);
		}
		if (max != null) {
			values.put(JobField.SALARY_MAX, max);
		}

		NormalizedJob job = ok(values);

		assertThat(job.salaryMin()).isEqualTo(expectedMin);
		assertThat(job.salaryMax()).isEqualTo(expectedMax);
	}

	@Test
	void numericSalaryValuesWork() {
		Map<JobField, Object> values = new EnumMap<>(validJob());
		values.put(JobField.SALARY_MIN, 45000.5);
		values.put(JobField.SALARY_MAX, 60000);

		NormalizedJob job = ok(values);

		assertThat(job.salaryMin()).isEqualTo(45000);
		assertThat(job.salaryMax()).isEqualTo(60000);
	}

	@Test
	void unknownCurrencyCodesAreDropped() {
		Map<JobField, Object> values = new EnumMap<>(validJob());
		values.put(JobField.CURRENCY, "rupees");

		assertThat(ok(values).currency()).isNull();
	}

	private NormalizedJob ok(Map<JobField, Object> values) {
		NormalizationResult result = normalizer.normalize(new RawJob(values), NOW);
		assertThat(result.isOk()).as(result.skipReason()).isTrue();
		return result.job();
	}

	private static Map<JobField, Object> validJob() {
		return Map.of(
				JobField.EXTERNAL_ID, "job-1",
				JobField.TITLE, "Backend Engineer",
				JobField.COMPANY, "Acme",
				JobField.APPLY_URL, "https://jobs.example.com/1");
	}

}
