package com.naukriradar.job.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;

import com.naukriradar.common.redis.lock.DistributedLock;
import com.naukriradar.common.redis.lock.LockHandle;
import com.naukriradar.common.redis.lock.LockUnavailableException;
import com.naukriradar.job.client.AiGatewayClient;
import com.naukriradar.job.config.AiParsingProperties;
import com.naukriradar.job.config.CacheConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Parses each job once with AI (required skills, years, seniority, work mode), and every
 * user's matching reuses the result. Parsing per job instead of per user and job is what
 * keeps AI affordable: 1,000 jobs cost 1,000 calls however many users there are.
 *
 * <p>Rounds run after every fetch, on a schedule and on request; one round at a time across
 * instances. A round stops at the first job AI can't answer for (AI off, out of budget,
 * down) and the rest wait for the next round.
 */
@Service
public class JobParsingService {

	private static final Logger log = LoggerFactory.getLogger(JobParsingService.class);

	static final String PROMPT = "job-parse";

	private static final String LOCK = "job-parsing";

	private static final int DESCRIPTION_CHARS = 6000;

	private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

	private final JdbcTemplate jdbc;
	private final AiGatewayClient ai;
	private final DistributedLock lock;
	private final AiParsingProperties properties;
	private final JsonMapper json;
	private final CacheManager caches;
	private final Clock clock = Clock.systemUTC();

	public JobParsingService(JdbcTemplate jdbc, AiGatewayClient ai, DistributedLock lock, AiParsingProperties properties,
			JsonMapper json, CacheManager caches) {
		this.jdbc = jdbc;
		this.ai = ai;
		this.lock = lock;
		this.properties = properties;
		this.json = json;
		this.caches = caches;
	}

	/** Starts a round on a virtual thread and returns at once. */
	public void parseInBackground() {
		if (properties.enabled()) {
			Thread.ofVirtual().name("job-parsing").start(this::parsePending);
		}
	}

	/** @return how many jobs were parsed */
	public int parsePending() {
		if (!properties.enabled()) {
			return 0;
		}
		Optional<LockHandle> held;
		try {
			held = lock.tryAcquire(LOCK);
		}
		catch (LockUnavailableException ex) {
			log.info("Job parsing skipped: {}", ex.getMessage());
			return 0;
		}
		if (held.isEmpty()) {
			return 0;
		}
		try (LockHandle ignored = held.get()) {
			return parseBatch();
		}
	}

	/** Marks every active job as unparsed, for after the prompt got better. @return how many */
	public int resetAll() {
		return jdbc.update("UPDATE jobs SET parsed_json = NULL, parsed_at = NULL WHERE status = 'ACTIVE'");
	}

	private int parseBatch() {
		List<Map<String, Object>> jobs = jdbc.queryForList("SELECT id, title, company, location, remote,"
				+ " SUBSTRING(description, 1, " + DESCRIPTION_CHARS + ") AS description FROM jobs"
				+ " WHERE status = 'ACTIVE' AND parsed_at IS NULL ORDER BY sort_at DESC LIMIT ?", properties.batchSize());
		int parsed = 0;
		for (Map<String, Object> job : jobs) {
			Optional<JsonNode> answer = ai.run(PROMPT, Map.of("job", describe(job)));
			if (answer.isEmpty()) {
				break;
			}
			jdbc.update(con -> {
				var ps = con.prepareStatement("UPDATE jobs SET parsed_json = ?, parsed_at = ? WHERE id = ?");
				ps.setString(1, json.writeValueAsString(answer.get()));
				ps.setTimestamp(2, Timestamp.from(clock.instant()), (Calendar) UTC.clone());
				ps.setString(3, (String) job.get("id"));
				return ps;
			});
			parsed++;
		}
		if (parsed > 0) {
			var detail = caches.getCache(CacheConfig.JOB_DETAIL);
			if (detail != null) {
				detail.clear();
			}
			log.info("Parsed {} of {} job(s) with AI", parsed, jobs.size());
		}
		return parsed;
	}

	private static String describe(Map<String, Object> job) {
		StringBuilder text = new StringBuilder();
		text.append("Title: ").append(job.get("title")).append('\n');
		text.append("Company: ").append(job.get("company")).append('\n');
		if (job.get("location") != null) {
			text.append("Location: ").append(job.get("location"))
					.append(Boolean.TRUE.equals(job.get("remote")) ? " (remote)" : "").append('\n');
		}
		if (job.get("description") != null) {
			text.append("Description:\n").append(job.get("description"));
		}
		return text.toString();
	}

}
