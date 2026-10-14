package com.naukriradar.job.search;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.job.config.CacheConfig;
import com.naukriradar.job.dto.request.JobSearchRequest;
import com.naukriradar.job.dto.request.JobSort;
import com.naukriradar.job.dto.response.JobDetailResponse;
import com.naukriradar.job.dto.response.JobSearchResponse;
import com.naukriradar.job.dto.response.JobSummaryResponse;
import com.naukriradar.job.model.JobStatus;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Searches active jobs, newest first.
 *
 * <ul>
 * <li>Words go through the FULLTEXT index ({@code MATCH ... AGAINST} in boolean mode), not
 * {@code LIKE '%word%'}, which can't use an index and scans every row.</li>
 * <li>Paging is keyset: "rows older than the last one I saw", answered from the
 * (status, sort_at, id) index. Page 1,000 costs the same as page 1, and a job arriving
 * while someone pages doesn't shift rows or show one twice.</li>
 * </ul>
 */
@Service
public class JobSearchService {

	private static final String COLUMNS = """
			id, source_code, external_id, title, company, location, remote, salary_min, salary_max,
			currency, posted_at, sort_at, apply_url""";

	/** The number a job is ranked by when sorting by salary; jobs without one get this sentinel, below any real pay. */
	private static final long NO_SALARY = -1;

	/** Rank by the top of the stated range, falling back to the bottom, then to "no salary". */
	private static final String SALARY_KEY = "COALESCE(salary_max, salary_min, " + NO_SALARY + ")";

	private final NamedParameterJdbcTemplate jdbc;
	private final JsonMapper json;
	private final Clock clock = Clock.systemUTC();

	public JobSearchService(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	public JobSearchResponse search(JobSearchRequest request) {
		int limit = request.limitOrDefault();
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("status", JobStatus.ACTIVE.name());
		StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM jobs WHERE status = :status");

		// "java, spring boot" is two keywords: a job matching either is found; the words of one keyword all must match
		List<String> keywords = new ArrayList<>();
		List<SearchTerms> groups = SearchTerms.groups(request.q());
		for (int g = 0; g < groups.size(); g++) {
			SearchTerms terms = groups.get(g);
			List<String> parts = new ArrayList<>();
			if (terms.fullText() != null) {
				parts.add("MATCH(title, company, description) AGAINST (:fullText" + g + " IN BOOLEAN MODE)");
				params.addValue("fullText" + g, terms.fullText());
			}
			for (int i = 0; i < terms.titlePatterns().size(); i++) {
				parts.add("title REGEXP :titlePattern" + g + "_" + i);
				params.addValue("titlePattern" + g + "_" + i, terms.titlePatterns().get(i));
			}
			if (!parts.isEmpty()) {
				keywords.add(String.join(" AND ", parts));
			}
		}
		if (keywords.size() == 1) {
			sql.append(" AND ").append(keywords.get(0));
		}
		else if (keywords.size() > 1) {
			sql.append(" AND ((").append(String.join(") OR (", keywords)).append("))");
		}
		LocationTerms place = LocationTerms.parse(request.location());
		if (place != null) {
			sql.append(place.remote() ? " AND (remote = TRUE OR REGEXP_LIKE(location, :place, 'i'))"
					: " AND REGEXP_LIKE(location, :place, 'i')");
			params.addValue("place", place.regexp());
		}
		else if (hasText(request.location())) {
			// only symbols were typed: nothing is in such a place
			sql.append(" AND 1 = 0");
		}
		if (request.remote() != null) {
			sql.append(" AND remote = :remote");
			params.addValue("remote", request.remote());
		}
		if (hasText(request.source())) {
			sql.append(" AND source_code = :source");
			params.addValue("source", request.source().strip());
		}
		if (hasText(request.region())) {
			// sources from before regions have none and count as GLOBAL
			sql.append(" AND source_code IN (SELECT code FROM job_sources WHERE COALESCE(region, 'GLOBAL') = :region)");
			params.addValue("region", request.region().strip());
		}
		if (request.postedWithinDays() != null) {
			sql.append(" AND sort_at >= :since");
			params.addValue("since", utc(clock.instant().minus(Duration.ofDays(request.postedWithinDays()))));
		}
		JobSort sort = request.sortOrDefault();
		if (hasText(request.cursor())) {
			SearchCursor cursor = SearchCursor.decode(request.cursor().strip());
			params.addValue("cursorSortAt", utc(cursor.sortAt()));
			params.addValue("cursorId", cursor.id());
			// row-by-row "after the last row I saw" in the chosen order, written so an index can seek to it
			switch (sort) {
				case NEWEST -> sql.append(" AND (sort_at < :cursorSortAt OR (sort_at = :cursorSortAt AND id < :cursorId))");
				case OLDEST -> sql.append(" AND (sort_at > :cursorSortAt OR (sort_at = :cursorSortAt AND id > :cursorId))");
				case SALARY -> {
					params.addValue("cursorRank", cursor.rank() == null ? NO_SALARY : cursor.rank());
					sql.append(" AND (").append(SALARY_KEY).append(" < :cursorRank OR (").append(SALARY_KEY)
							.append(" = :cursorRank AND (sort_at < :cursorSortAt OR (sort_at = :cursorSortAt AND id < :cursorId))))");
				}
			}
		}
		// one extra row tells us whether there is a next page
		sql.append(switch (sort) {
			case NEWEST -> " ORDER BY sort_at DESC, id DESC";
			case OLDEST -> " ORDER BY sort_at ASC, id ASC";
			case SALARY -> " ORDER BY " + SALARY_KEY + " DESC, sort_at DESC, id DESC";
		});
		sql.append(" LIMIT :limit");
		params.addValue("limit", limit + 1);

		List<Row> rows = jdbc.query(sql.toString(), params, (rs, n) -> row(rs));
		boolean more = rows.size() > limit;
		List<Row> page = more ? rows.subList(0, limit) : rows;
		Row last = page.isEmpty() ? null : page.get(page.size() - 1);
		Long rank = sort == JobSort.SALARY && last != null ? effectiveSalary(last) : null;
		String nextCursor = more ? new SearchCursor(last.sortAt(), last.summary().id(), rank).encode() : null;
		return new JobSearchResponse(page.stream().map(Row::summary).toList(), nextCursor);
	}

	/** Cached; {@code sync} sends concurrent misses for one job through the stampede guard. */
	@Cacheable(cacheNames = CacheConfig.JOB_DETAIL, sync = true)
	public JobDetailResponse get(String id) {
		List<JobDetailResponse> found = jdbc.query("SELECT " + COLUMNS
				+ ", description, status, fetched_at, last_seen_at, parsed_json, parsed_at FROM jobs WHERE id = :id",
				new MapSqlParameterSource("id", id), (rs, n) -> {
					JobSummaryResponse s = row(rs).summary();
					return new JobDetailResponse(s.id(), s.sourceCode(), rs.getString("external_id"), s.title(),
							s.company(), s.location(), s.remote(), s.salaryMin(), s.salaryMax(), s.currency(),
							s.postedAt(), s.applyUrl(), rs.getString("description"),
							JobStatus.valueOf(rs.getString("status")), instant(rs, "fetched_at"),
							instant(rs, "last_seen_at"), requirements(rs.getString("parsed_json")), instant(rs, "parsed_at"));
				});
		if (found.isEmpty()) {
			throw new NotFoundException("No job " + id + ".");
		}
		return found.get(0);
	}

	/** The salary this row is ranked by: the same value {@link #SALARY_KEY} computes in SQL. */
	private static long effectiveSalary(Row row) {
		Long max = row.summary().salaryMax();
		if (max != null) {
			return max;
		}
		Long min = row.summary().salaryMin();
		return min != null ? min : NO_SALARY;
	}

	private JsonNode requirements(String parsedJson) {
		if (parsedJson == null) {
			return null;
		}
		try {
			return json.readTree(parsedJson);
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

	private static Row row(ResultSet rs) throws SQLException {
		JobSummaryResponse summary = new JobSummaryResponse(
				rs.getString("id"),
				rs.getString("source_code"),
				rs.getString("title"),
				rs.getString("company"),
				rs.getString("location"),
				rs.getBoolean("remote"),
				nullableLong(rs, "salary_min"),
				nullableLong(rs, "salary_max"),
				rs.getString("currency"),
				instant(rs, "posted_at"),
				rs.getString("apply_url"));
		return new Row(summary, instant(rs, "sort_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp value = rs.getTimestamp(column, Calendar.getInstance(TimeZone.getTimeZone("UTC")));
		return value == null ? null : value.toInstant();
	}

	private static Long nullableLong(ResultSet rs, String column) throws SQLException {
		long value = rs.getLong(column);
		return rs.wasNull() ? null : value;
	}

	/** DATETIME columns hold UTC wall-clock time; a LocalDateTime is bound as is, with no zone shift. */
	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private record Row(JobSummaryResponse summary, Instant sortAt) {
	}

}
