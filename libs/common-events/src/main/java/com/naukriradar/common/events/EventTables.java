package com.naukriradar.common.events;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Creates the event tables in the service's own database if they aren't there. Plain JDBC,
 * so each service keeps these tables without scanning entities from a library; Flyway takes
 * this over later.
 */
public class EventTables {

	private final JdbcTemplate jdbc;

	public EventTables(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public void create() {
		jdbc.execute("""
				CREATE TABLE IF NOT EXISTS outbox (
				  id VARCHAR(36) NOT NULL PRIMARY KEY,
				  topic VARCHAR(100) NOT NULL,
				  event_key VARCHAR(200) NOT NULL,
				  event_type VARCHAR(100) NOT NULL,
				  payload MEDIUMTEXT NOT NULL,
				  created_at DATETIME(6) NOT NULL,
				  published_at DATETIME(6) NULL,
				  attempts INT NOT NULL DEFAULT 0,
				  last_error VARCHAR(500) NULL,
				  KEY idx_outbox_pending (published_at, created_at)
				)""");
		jdbc.execute("""
				CREATE TABLE IF NOT EXISTS processed_events (
				  consumer VARCHAR(100) NOT NULL,
				  event_id VARCHAR(36) NOT NULL,
				  processed_at DATETIME(6) NOT NULL,
				  PRIMARY KEY (consumer, event_id)
				)""");
		jdbc.execute("""
				CREATE TABLE IF NOT EXISTS dead_letters (
				  id VARCHAR(36) NOT NULL PRIMARY KEY,
				  topic VARCHAR(100) NOT NULL,
				  event_key VARCHAR(200) NULL,
				  payload MEDIUMTEXT NOT NULL,
				  error VARCHAR(1000) NULL,
				  failed_at DATETIME(6) NOT NULL,
				  replayed_at DATETIME(6) NULL,
				  KEY idx_dead_letters_topic (topic, failed_at)
				)""");
	}

}
