package com.naukriradar.job.search;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.job.config.CacheConfig;
import com.naukriradar.job.dto.request.JobSearchRequest;
import com.naukriradar.job.dto.response.JobDetailResponse;
import com.naukriradar.job.dto.response.JobSearchResponse;
import com.naukriradar.job.dto.response.JobSummaryResponse;
import com.naukriradar.job.model.JobStatus;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

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

	private final NamedParameterJdbcTemplate jdbc;
	private final Clock clock = Clock.systemUTC();

	public JobSearchService(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public JobSearchResponse search(JobSearchRequest request) {
		int limit = request.limitOrDefault();
		MapSqlParameterSource params = new MapSqlParameterSource().addValue("status", JobStatus.ACTIVE.name());
		StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM jobs WHERE status = :status");

		SearchTerms terms = SearchTerms.parse(request.q());
		if (terms.fullText() != null) {
			sql.append(" AND MATCH(title, company, description) AGAINST (:fullText IN BOOLEAN MODE)");
			params.addValue("fullText", terms.fullText());
		}
		for (int i = 0; i < terms.titlePatterns().size(); i++) {
			sql.append(" AND title REGEXP :titlePattern").append(i);
			params.addValue("titlePattern" + i, terms.titlePatterns().get(i));
		}
		if (hasText(request.location())) {
			sql.append(" AND location LIKE :location ESCAPE '!'");
			params.addValue("location", "%" + escapeLike(request.location().strip()) + "%");
		}
		if (request.remote() != null) {
			sql.append(" AND remote = :remote");
			params.addValue("remote", request.remote());
		}
		if (hasText(request.source())) {
			sql.append(" AND source_code = :source");
			params.addValue("source", request.source().strip());
		}
		if (request.postedWithinDays() != null) {
			sql.append(" AND sort_at >= :since");
			params.addValue("since", utc(clock.instant().minus(Duration.ofDays(request.postedWithinDays()))));
		}
		if (hasText(request.cursor())) {
			SearchCursor cursor = SearchCursor.decode(request.cursor().strip());
			// row-by-row "(sort_at, id) < (cursorSortAt, cursorId)", written so MySQL can use the index
			sql.append(" AND (sort_at < :cursorSortAt OR (sort_at = :cursorSortAt AND id < :cursorId))");
			params.addValue("cursorSortAt", utc(cursor.sortAt()));
			params.addValue("cursorId", cursor.id());
		}
		// one extra row tells us whether there is a next page
		sql.append(" ORDER BY sort_at DESC, id DESC LIMIT :limit");
		params.addValue("limit", limit + 1);

		List<Row> rows = jdbc.query(sql.toString(), params, (rs, n) -> row(rs));
		boolean more = rows.size() > limit;
		List<Row> page = more ? rows.subList(0, limit) : rows;
		Row last = page.isEmpty() ? null : page.get(page.size() - 1);
		String nextCursor = more ? new SearchCursor(last.sortAt(), last.summary().id()).encode() : null;
		return new JobSearchResponse(page.stream().map(Row::summary).toList(), nextCursor);
	}

	/** Cached; {@code sync} sends concurrent misses for one job through the stampede guard. */
	@Cacheable(cacheNames = CacheConfig.JOB_DETAIL, sync = true)
	public JobDetailResponse get(String id) {
		List<JobDetailResponse> found = jdbc.query("SELECT " + COLUMNS
				+ ", description, status, fetched_at, last_seen_at FROM jobs WHERE id = :id",
				new MapSqlParameterSource("id", id), (rs, n) -> {
					JobSummaryResponse s = row(rs).summary();
					return new JobDetailResponse(s.id(), s.sourceCode(), rs.getString("external_id"), s.title(),
							s.company(), s.location(), s.remote(), s.salaryMin(), s.salaryMax(), s.currency(),
							s.postedAt(), s.applyUrl(), rs.getString("description"),
							JobStatus.valueOf(rs.getString("status")), instant(rs, "fetched_at"),
							instant(rs, "last_seen_at"));
				});
		if (found.isEmpty()) {
			throw new NotFoundException("No job " + id + ".");
		}
		return found.get(0);
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

	/** % and _ in user input are literal, not wildcards. */
	private static String escapeLike(String value) {
		return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	private record Row(JobSummaryResponse summary, Instant sortAt) {
	}

}
