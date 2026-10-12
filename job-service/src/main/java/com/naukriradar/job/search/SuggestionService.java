package com.naukriradar.job.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.naukriradar.job.dto.response.Suggestion;
import com.naukriradar.job.model.JobStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * What to offer while someone types in the job search: places that active jobs are really in
 * (with how many jobs each), and job titles and skills for the search box. Matches start at a
 * word, so "pu" offers Pune, not Kanpur.
 */
@Service
public class SuggestionService {

	static final int MAX = 8;

	/** Location strings read per request; enough to cover the common places. */
	private static final int SCAN = 300;

	/** "Bengaluru, Karnataka, India" -> its parts; "Hybrid - London" -> Hybrid, London. */
	private static final Pattern PARTS = Pattern.compile("\\s*(?:[,;/|()]|\\s-\\s)\\s*");

	private static final List<String> SKILLS = List.of("Java", "Spring Boot", "Spring", "Microservices", "Hibernate", "JPA",
			"Kafka", "Redis", "MySQL", "PostgreSQL", "MongoDB", "SQL", "REST API", "GraphQL", "Docker", "Kubernetes", "AWS", "Azure",
			"GCP", "Jenkins", "CI/CD", "Git", "Linux", "Python", "Django", "Flask", "FastAPI", "Go", "Golang", "Node.js",
			"JavaScript", "TypeScript", "React", "Angular", "Vue", "Next.js", "HTML", "CSS", "Kotlin", "Android", "iOS", "Swift",
			"Flutter", "React Native", "C#", ".NET", "C++", "Rust", "Scala", "Spark", "Hadoop", "Airflow", "Data Engineer",
			"Machine Learning", "Deep Learning", "LLM", "NLP", "Data Science", "Power BI", "Tableau", "Excel", "Selenium",
			"Playwright", "QA", "DevOps", "SRE", "Terraform", "Backend", "Frontend", "Full Stack", "Product Manager",
			"Business Analyst", "UI/UX", "Figma");

	private final NamedParameterJdbcTemplate jdbc;

	public SuggestionService(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public List<Suggestion> locations(String input) {
		LocationTerms terms = LocationTerms.parse(input);
		if (terms == null) {
			return List.of();
		}
		String typed = terms.typed();
		List<Suggestion> out = new ArrayList<>();
		for (String alias : LocationTerms.matchingAliases(typed)) {
			LocationTerms aliasTerms = LocationTerms.parse(alias);
			long count = countLocation(aliasTerms);
			if (count > 0) {
				out.add(new Suggestion(title(alias), "place", count));
			}
		}

		List<String> prefixes = LocationTerms.typedPrefixes(typed);
		MapSqlParameterSource params = new MapSqlParameterSource()
				.addValue("status", JobStatus.ACTIVE.name())
				// names hold only letters, digits, spaces, dots and dashes (see LocationTerms)
				.addValue("place", "\\b(" + String.join("|", prefixes.stream().map(p -> p.replace(".", "\\.")).toList()) + ")")
				.addValue("scan", SCAN);
		Map<String, Long> places = new LinkedHashMap<>();
		Map<String, String> display = new LinkedHashMap<>();
		jdbc.query("""
				SELECT location, COUNT(*) AS total FROM jobs
				WHERE status = :status AND location IS NOT NULL AND REGEXP_LIKE(location, :place, 'i')
				GROUP BY location ORDER BY total DESC LIMIT :scan""", params, rs -> {
			long total = rs.getLong("total");
			for (String part : PARTS.split(rs.getString("location"))) {
				String place = part.strip();
				if (place.length() < 2 || prefixes.stream().noneMatch(p -> LocationTerms.startsWithTyped(place, p))) {
					continue;
				}
				String key = place.toLowerCase(Locale.ROOT);
				places.merge(key, total, Long::sum);
				// boards write "bengaluru" and "Bengaluru"; show the capitalised one
				display.merge(key, place, (shown, other) -> Character.isUpperCase(shown.charAt(0)) ? shown : other);
			}
		});
		places.entrySet().stream()
				.sorted(Map.Entry.<String, Long>comparingByValue().reversed())
				.filter(e -> out.stream().noneMatch(s -> s.label().equalsIgnoreCase(display.get(e.getKey()))))
				.limit(MAX)
				.forEach(e -> out.add(new Suggestion(capitalised(display.get(e.getKey())), "place", e.getValue())));
		return out.stream().limit(MAX).toList();
	}

	/** Skills first (they are what people mostly type), then real job titles. */
	public List<Suggestion> keywords(String input) {
		String typed = input == null ? "" : input.strip().toLowerCase(Locale.ROOT);
		if (typed.length() < 2) {
			return List.of();
		}
		List<Suggestion> out = new ArrayList<>();
		SKILLS.stream()
				.filter(skill -> LocationTerms.startsWithTyped(skill, typed))
				.sorted(Comparator.comparingInt(String::length))
				.limit(4)
				.forEach(skill -> out.add(new Suggestion(skill, "skill", null)));
		String word = typed.replaceAll("[^\\p{L}\\p{N} ]", " ").strip();
		if (!word.isEmpty()) {
			MapSqlParameterSource params = new MapSqlParameterSource()
					.addValue("status", JobStatus.ACTIVE.name())
					.addValue("pattern", "\\b" + word.replaceAll("\\s+", "\\\\s+"))
					.addValue("limit", MAX);
			jdbc.query("""
					SELECT title, COUNT(*) AS total FROM jobs
					WHERE status = :status AND REGEXP_LIKE(title, :pattern, 'i')
					GROUP BY title ORDER BY total DESC, title LIMIT :limit""", params, rs -> {
				out.add(new Suggestion(rs.getString("title"), "title", rs.getLong("total")));
			});
		}
		return out.stream().limit(MAX).toList();
	}

	private long countLocation(LocationTerms terms) {
		String sql = "SELECT COUNT(*) FROM jobs WHERE status = :status AND "
				+ (terms.remote() ? "(remote = TRUE OR REGEXP_LIKE(location, :place, 'i'))" : "REGEXP_LIKE(location, :place, 'i')");
		Long count = jdbc.queryForObject(sql, new MapSqlParameterSource()
				.addValue("status", JobStatus.ACTIVE.name())
				.addValue("place", terms.regexp()), Long.class);
		return count == null ? 0 : count;
	}

	/** "bengaluru" -> "Bengaluru"; names already written with capitals are left alone. */
	static String capitalised(String place) {
		if (!place.equals(place.toLowerCase(Locale.ROOT))) {
			return place;
		}
		StringBuilder out = new StringBuilder(place.length());
		boolean start = true;
		for (char c : place.toCharArray()) {
			out.append(start ? Character.toUpperCase(c) : c);
			start = !Character.isLetterOrDigit(c);
		}
		return out.toString();
	}

	private static String title(String alias) {
		return switch (alias) {
			case "india" -> "India";
			case "remote" -> "Remote";
			case "delhi ncr" -> "Delhi NCR";
			default -> alias;
		};
	}

}
