package com.naukriradar.core.service;

import java.util.UUID;

import com.naukriradar.common.redis.run.RunLeases;
import com.naukriradar.core.model.ApplyRunStatus;
import com.naukriradar.core.repository.ApplyRunRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** With several instances, only runs nobody is working on may be closed as interrupted. */
@SpringBootTest
@ActiveProfiles("test")
class ApplyRunSweepIT {

	@Autowired
	private ApplyRunStore store;

	@Autowired
	private ApplyRunRepository repository;

	@Autowired
	private RunLeases leases;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void onlyOldRunsWithoutALeaseAreClosed() {
		String crashed = store.create(UUID.randomUUID().toString()).getId();
		String live = store.create(UUID.randomUUID().toString()).getId();
		String fresh = store.create(UUID.randomUUID().toString()).getId();
		// lease first: the background sweep may run at any moment
		leases.begin(ApplyRunStore.LEASE, live);
		jdbc.update("UPDATE apply_runs SET started_at = started_at - INTERVAL 5 MINUTE WHERE id IN (?, ?)", crashed, live);
		try {
			store.closeInterruptedRuns();
		}
		finally {
			leases.end(ApplyRunStore.LEASE, live);
		}

		assertThat(repository.findById(crashed).orElseThrow().getStatus()).isEqualTo(ApplyRunStatus.FAILED);
		assertThat(repository.findById(crashed).orElseThrow().getMessage()).contains("Interrupted");
		assertThat(repository.findById(live).orElseThrow().getStatus()).isEqualTo(ApplyRunStatus.RUNNING);
		assertThat(repository.findById(fresh).orElseThrow().getStatus()).isEqualTo(ApplyRunStatus.RUNNING);
		jdbc.update("UPDATE apply_runs SET status = 'FAILED', running_user_id = NULL WHERE id IN (?, ?)", live, fresh);
	}

}
