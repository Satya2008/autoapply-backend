package com.naukriradar.common.events;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Calendar;
import java.util.TimeZone;

import org.springframework.jdbc.core.JdbcTemplate;

/** How the outbox is doing: a pending count that keeps growing means the relay is stuck. */
public class OutboxStats {

	private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

	private final JdbcTemplate jdbc;

	public OutboxStats(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Stats stats() {
		return jdbc.queryForObject("""
				SELECT SUM(published_at IS NULL) AS pending,
				       MIN(CASE WHEN published_at IS NULL THEN created_at END) AS oldest,
				       SUM(published_at IS NULL AND attempts > 0) AS failing,
				       SUM(published_at >= UTC_TIMESTAMP(6) - INTERVAL 1 HOUR) AS sent_last_hour
				FROM outbox""", (rs, n) -> {
			Timestamp oldest = rs.getTimestamp("oldest", (Calendar) UTC.clone());
			Long lag = oldest == null ? null : Duration.between(oldest.toInstant(), Instant.now()).toSeconds();
			return new Stats(rs.getLong("pending"), lag, rs.getLong("failing"), rs.getLong("sent_last_hour"));
		});
	}

	/**
	 * @param oldestPendingSeconds how long the oldest unsent event has waited; null when none
	 * @param failing pending events whose last send failed
	 */
	public record Stats(long pending, Long oldestPendingSeconds, long failing, long sentLastHour) {
	}

}
