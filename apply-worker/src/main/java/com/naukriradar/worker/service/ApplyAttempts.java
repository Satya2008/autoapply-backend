package com.naukriradar.worker.service;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Calendar;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Every attempt is written down before the browser touches the form. That is what makes a
 * crash safe: if the worker dies after pressing submit but before reporting, the redelivered
 * event finds an attempt that STARTED and never finished. Submitting again could send the
 * employer a second application, so the worker reports "unknown" and the candidate checks.
 */
@Component
public class ApplyAttempts {

	private static final Calendar UTC = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

	private final JdbcTemplate jdbc;
	private final Clock clock = Clock.systemUTC();

	public ApplyAttempts(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
		// now, not at "ready": the Kafka listeners start before that
		createTable();
	}

	private void createTable() {
		jdbc.execute("""
				CREATE TABLE IF NOT EXISTS apply_attempts (
				  application_id VARCHAR(36) NOT NULL,
				  attempt INT NOT NULL,
				  status VARCHAR(20) NOT NULL,
				  outcome VARCHAR(20) NULL,
				  started_at DATETIME(6) NOT NULL,
				  finished_at DATETIME(6) NULL,
				  PRIMARY KEY (application_id, attempt)
				)""");
	}

	/** @return empty if this attempt is new and now started; else the status it already had */
	public Optional<String> begin(String applicationId, int attempt) {
		try {
			jdbc.update(con -> {
				var ps = con.prepareStatement("INSERT INTO apply_attempts (application_id, attempt, status, started_at)"
						+ " VALUES (?, ?, 'STARTED', ?)");
				ps.setString(1, applicationId);
				ps.setInt(2, attempt);
				ps.setTimestamp(3, Timestamp.from(clock.instant()), (Calendar) UTC.clone());
				return ps;
			});
			return Optional.empty();
		}
		catch (DuplicateKeyException ex) {
			List<String> status = jdbc.queryForList("SELECT status FROM apply_attempts WHERE application_id = ? AND attempt = ?",
					String.class, applicationId, attempt);
			return Optional.of(status.isEmpty() ? "STARTED" : status.get(0));
		}
	}

	public void finish(String applicationId, int attempt, String outcome) {
		jdbc.update(con -> {
			var ps = con.prepareStatement("UPDATE apply_attempts SET status = 'FINISHED', outcome = ?, finished_at = ?"
					+ " WHERE application_id = ? AND attempt = ?");
			ps.setString(1, outcome);
			ps.setTimestamp(2, Timestamp.from(clock.instant()), (Calendar) UTC.clone());
			ps.setString(3, applicationId);
			ps.setInt(4, attempt);
			return ps;
		});
	}

}
