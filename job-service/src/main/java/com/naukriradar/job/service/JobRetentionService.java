package com.naukriradar.job.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;

import com.naukriradar.job.config.CacheConfig;
import com.naukriradar.job.config.JobScheduleProperties;
import com.naukriradar.job.dto.response.CleanupResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Keeps the jobs table small and honest. A job no board has returned for a week is marked
 * CLOSED (it is probably filled); after 60 days it is deleted. Deletes go in small batches
 * so a big cleanup never locks the table for long.
 */
@Service
public class JobRetentionService {

	private static final Logger log = LoggerFactory.getLogger(JobRetentionService.class);

	private static final int DELETE_BATCH = 1000;

	private final JdbcTemplate jdbc;
	private final JobScheduleProperties properties;
	private final Clock clock;

	public JobRetentionService(JdbcTemplate jdbc, JobScheduleProperties properties) {
		this.jdbc = jdbc;
		this.properties = properties;
		this.clock = Clock.systemUTC();
	}

	@CacheEvict(cacheNames = CacheConfig.JOB_DETAIL, allEntries = true)
	public CleanupResponse cleanUp() {
		Instant now = clock.instant();
		Timestamp closeBefore = Timestamp.from(now.minus(Duration.ofDays(properties.closeAfterDays())));
		Timestamp deleteBefore = Timestamp.from(now.minus(Duration.ofDays(properties.deleteAfterDays())));
		Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

		int closed = jdbc.update(con -> {
			var ps = con.prepareStatement(
					"UPDATE jobs SET status = 'CLOSED', version = version + 1 WHERE status = 'ACTIVE' AND last_seen_at < ?");
			ps.setTimestamp(1, closeBefore, utc);
			return ps;
		});

		int deleted = 0;
		int batch;
		do {
			batch = jdbc.update(con -> {
				var ps = con.prepareStatement("DELETE FROM jobs WHERE last_seen_at < ? LIMIT " + DELETE_BATCH);
				ps.setTimestamp(1, deleteBefore, utc);
				return ps;
			});
			deleted += batch;
		}
		while (batch == DELETE_BATCH);

		if (closed > 0 || deleted > 0) {
			log.info("Job cleanup: {} closed, {} deleted", closed, deleted);
		}
		return new CleanupResponse(closed, deleted);
	}

}
