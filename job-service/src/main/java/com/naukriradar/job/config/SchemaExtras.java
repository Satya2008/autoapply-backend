package com.naukriradar.job.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Schema pieces JPA can't describe, created at startup if missing. Moves into a Flyway
 * migration in Phase 9.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class SchemaExtras implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(SchemaExtras.class);

	static final String FULLTEXT_INDEX = "ft_jobs_text";

	private final JdbcTemplate jdbc;

	@Override
	public void run(ApplicationArguments args) {
		Integer existing = jdbc.queryForObject("""
				SELECT COUNT(*) FROM information_schema.statistics
				WHERE table_schema = DATABASE() AND table_name = 'jobs' AND index_name = ?
				""", Integer.class, FULLTEXT_INDEX);
		if (existing == null || existing == 0) {
			jdbc.execute("CREATE FULLTEXT INDEX " + FULLTEXT_INDEX + " ON jobs (title, company, description)");
			log.info("Created FULLTEXT index {} on jobs", FULLTEXT_INDEX);
		}
	}

}
