package com.naukriradar.job.search;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.naukriradar.job.dto.request.CandidateQuery;
import com.naukriradar.job.dto.response.CandidateJobResponse;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Shortlists jobs for a candidate so matching scores a few hundred, not every job. Uses
 * FULLTEXT in natural language mode: any of the words may match and MySQL ranks by how well,
 * which is what a shortlist needs (boolean "+word" would demand every skill at once).
 */
@Service
public class CandidateJobFinder {

	static final int DESCRIPTION_CHARS = 5000;

	static final int MAX_WORDS = 60;

	private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]{" + SearchTerms.MIN_FULLTEXT_TOKEN + ",84}");

	private static final String COLUMNS = """
			id, title, company, location, remote, salary_min, salary_max, currency, posted_at, apply_url,
			SUBSTRING(description, 1, %d) AS description, parsed_json""".formatted(DESCRIPTION_CHARS);

	private final NamedParameterJdbcTemplate jdbc;
	private final JsonMapper json;
	private final Clock clock = Clock.systemUTC();

	public CandidateJobFinder(NamedParameterJdbcTemplate jdbc, JsonMapper json) {
		this.jdbc = jdbc;
		this.json = json;
	}

	public List<CandidateJobResponse> find(CandidateQuery query) {
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("since", LocalDateTime.ofInstant(clock.instant().minus(Duration.ofDays(query.daysOrDefault())), ZoneOffset.UTC))
				.addValue("limit", query.limitOrDefault());
		String words = words(query.keywords());
		String sql;
		if (words.isEmpty()) {
			// nothing to rank by: newest jobs are the best guess
			sql = "SELECT " + COLUMNS + " FROM jobs WHERE status = 'ACTIVE' AND sort_at >= :since"
					+ " ORDER BY sort_at DESC, id DESC LIMIT :limit";
		}
		else {
			params.addValue("words", words);
			sql = "SELECT " + COLUMNS + ", MATCH(title, company, description) AGAINST (:words) AS relevance"
					+ " FROM jobs WHERE status = 'ACTIVE' AND sort_at >= :since"
					+ " AND MATCH(title, company, description) AGAINST (:words)"
					+ " ORDER BY relevance DESC, sort_at DESC, id DESC LIMIT :limit";
		}
		return jdbc.query(sql, params, (rs, n) -> row(rs));
	}

	/** Plain words only: symbols can't be indexed, and they'd mean nothing special here anyway. */
	static String words(List<String> keywords) {
		Set<String> words = new LinkedHashSet<>();
		for (String keyword : keywords) {
			Matcher matcher = WORD.matcher(keyword.toLowerCase(Locale.ROOT));
			while (matcher.find() && words.size() < MAX_WORDS) {
				words.add(matcher.group());
			}
		}
		return String.join(" ", words);
	}

	private CandidateJobResponse row(ResultSet rs) throws SQLException {
		long salaryMin = rs.getLong("salary_min");
		boolean noMin = rs.wasNull();
		long salaryMax = rs.getLong("salary_max");
		boolean noMax = rs.wasNull();
		Timestamp posted = rs.getTimestamp("posted_at", Calendar.getInstance(TimeZone.getTimeZone("UTC")));
		Parsed parsed = parsed(rs.getString("parsed_json"));
		return new CandidateJobResponse(rs.getString("id"), rs.getString("title"), rs.getString("company"),
				rs.getString("location"), rs.getBoolean("remote"), noMin ? null : salaryMin, noMax ? null : salaryMax,
				rs.getString("currency"), posted == null ? null : posted.toInstant(), rs.getString("apply_url"),
				rs.getString("description"), parsed.requiredSkills(), parsed.minYears(), parsed.seniority());
	}

	/** The parts of the AI parse that scoring uses; empty when not parsed (or unreadable). */
	private Parsed parsed(String parsedJson) {
		if (parsedJson == null) {
			return Parsed.NONE;
		}
		try {
			JsonNode node = json.readTree(parsedJson);
			List<String> skills = new ArrayList<>();
			node.path("requiredSkills").forEach(skill -> skills.add(skill.asString()));
			JsonNode years = node.path("minYearsExperience");
			String seniority = node.path("seniority").isString() ? node.path("seniority").asString() : null;
			return new Parsed(skills, years.isNumber() ? years.asInt() : null,
					"unknown".equals(seniority) ? null : seniority);
		}
		catch (RuntimeException ex) {
			return Parsed.NONE;
		}
	}

	private record Parsed(List<String> requiredSkills, Integer minYears, String seniority) {

		static final Parsed NONE = new Parsed(List.of(), null, null);
	}

}
