package com.naukriradar.core.scheduler;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.naukriradar.core.service.NotificationRequests;
import com.naukriradar.core.settings.SettingDefinitions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Each user's last 24 hours, from the real numbers: applications sent, ones waiting for the
 * user, new ones picked. A user with nothing to report gets nothing: a digest of zeros is
 * noise, and one of those was a bug in the old version.
 */
@Component
public class DailyDigestJob implements ScheduledJob {

	private static final Logger log = LoggerFactory.getLogger(DailyDigestJob.class);

	private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

	private final JdbcTemplate jdbc;
	private final NotificationRequests notifications;
	private final TransactionTemplate transaction;
	private final Clock clock = Clock.systemUTC();

	public DailyDigestJob(JdbcTemplate jdbc, NotificationRequests notifications, PlatformTransactionManager transactionManager) {
		this.jdbc = jdbc;
		this.notifications = notifications;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	@Override
	public String name() {
		return "daily-digest";
	}

	@Override
	public String cronSetting() {
		return SettingDefinitions.DIGEST_CRON;
	}

	@Override
	public String enabledSetting() {
		return SettingDefinitions.DIGEST_ENABLED;
	}

	@Override
	public String run() {
		Timestamp since = Timestamp.from(clock.instant().minus(Duration.ofDays(1)));
		List<String> users = jdbc.queryForList("SELECT id FROM users", String.class);
		int sent = 0;
		int quiet = 0;
		for (String userId : users) {
			Map<String, Object> numbers = numbers(userId, since);
			if ((int) numbers.get("submitted") + (int) numbers.get("needsYou") + (int) numbers.get("newApplications") == 0) {
				quiet++;
				continue;
			}
			try {
				Boolean requested = transaction.execute(status -> notifications.request(userId,
						NotificationRequests.Kind.DIGEST, "daily-digest", numbers));
				if (Boolean.TRUE.equals(requested)) {
					sent++;
				}
			}
			catch (RuntimeException ex) {
				log.warn("Digest for {} failed: {}", userId, ex.getMessage());
			}
		}
		return "Digest for " + sent + " users; " + quiet + " had nothing new.";
	}

	Map<String, Object> numbers(String userId, Timestamp since) {
		Map<String, Object> numbers = new HashMap<>();
		numbers.put("submitted", count("SELECT COUNT(*) FROM applications WHERE user_id = ?"
				+ " AND status IN ('SUBMITTED', 'SIMULATED') AND updated_at >= ?", userId, since));
		// everything still waiting, however old: that is what the user has to do
		Integer waitingCount = jdbc.queryForObject("SELECT COUNT(*) FROM applications WHERE user_id = ?"
				+ " AND status = 'NEEDS_YOU'", Integer.class, userId);
		numbers.put("needsYou", waitingCount == null ? 0 : waitingCount);
		numbers.put("newApplications", count("SELECT COUNT(*) FROM applications WHERE user_id = ? AND created_at >= ?",
				userId, since));
		List<Map<String, Object>> waiting = jdbc.queryForList("SELECT job_title AS title, job_company AS company"
				+ " FROM applications WHERE user_id = ? AND status = 'NEEDS_YOU' ORDER BY match_score DESC LIMIT 3", userId);
		numbers.put("topWaiting", waiting);
		numbers.put("hasWaiting", !waiting.isEmpty());
		return numbers;
	}

	private int count(String sql, String userId, Timestamp since) {
		Integer n = jdbc.query(con -> {
			var ps = con.prepareStatement(sql);
			ps.setString(1, userId);
			ps.setTimestamp(2, since, (Calendar) UTC.clone());
			return ps;
		}, rs -> rs.next() ? rs.getInt(1) : 0);
		return n == null ? 0 : n;
	}

}
