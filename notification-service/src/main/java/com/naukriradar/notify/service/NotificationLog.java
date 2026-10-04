package com.naukriradar.notify.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * What was sent, per event and channel. The key is (event, channel): if email went out and
 * Telegram failed, the retry sends only Telegram, and a second delivery of the event sends
 * nothing at all.
 */
@Component
public class NotificationLog {

	private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

	private final JdbcTemplate jdbc;
	private final Clock clock = Clock.systemUTC();

	public NotificationLog(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		// now, not at "ready": the Kafka listeners start before that
		jdbc.execute("""
				CREATE TABLE IF NOT EXISTS notification_log (
				  event_id VARCHAR(36) NOT NULL,
				  channel VARCHAR(20) NOT NULL,
				  user_id VARCHAR(36) NULL,
				  template VARCHAR(50) NOT NULL,
				  status VARCHAR(10) NOT NULL,
				  attempts INT NOT NULL DEFAULT 0,
				  last_error VARCHAR(500) NULL,
				  updated_at DATETIME(6) NOT NULL,
				  PRIMARY KEY (event_id, channel),
				  KEY idx_notification_log_user (user_id, updated_at)
				)""");
	}

	public boolean sent(String eventId, String channel) {
		List<String> status = jdbc.queryForList("SELECT status FROM notification_log WHERE event_id = ? AND channel = ?",
				String.class, eventId, channel);
		return !status.isEmpty() && "SENT".equals(status.get(0));
	}

	public void record(String eventId, String channel, String userId, String template, boolean ok, String error) {
		jdbc.update(con -> {
			var ps = con.prepareStatement("""
					INSERT INTO notification_log (event_id, channel, user_id, template, status, attempts, last_error, updated_at)
					VALUES (?, ?, ?, ?, ?, 1, ?, ?) AS new
					ON DUPLICATE KEY UPDATE status = new.status, attempts = notification_log.attempts + 1,
					  last_error = new.last_error, updated_at = new.updated_at""");
			ps.setString(1, eventId);
			ps.setString(2, channel);
			ps.setString(3, userId);
			ps.setString(4, template);
			ps.setString(5, ok ? "SENT" : "FAILED");
			ps.setString(6, error == null || error.length() <= 500 ? error : error.substring(0, 500));
			ps.setTimestamp(7, Timestamp.from(clock.instant()), (Calendar) UTC.clone());
			return ps;
		});
	}

}
