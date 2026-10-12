package com.naukriradar.job.service;

import java.time.Instant;

import com.naukriradar.job.model.JobSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FetchDueTest {

	private static final Instant NINE_AM = Instant.parse("2026-10-12T03:30:00Z");

	@Test
	void aSourceWithoutAnIntervalRunsEveryTime() {
		JobSource source = new JobSource("board");
		source.setLastRunAt(NINE_AM.minusSeconds(60));

		assertThat(JobFetchOrchestrator.due(source, NINE_AM)).isTrue();
	}

	@Test
	void aDailySourceRunsAtTheSameTimeNextDayButNotAtOneOClock() {
		JobSource source = new JobSource("jsearch");
		source.setFetchEveryHours(24);
		// yesterday's 9:00 run ended a couple of minutes after nine
		source.setLastRunAt(NINE_AM.minusSeconds(24 * 3600 - 150));

		assertThat(JobFetchOrchestrator.due(source, NINE_AM)).isTrue();
		source.setLastRunAt(NINE_AM.plusSeconds(120));
		assertThat(JobFetchOrchestrator.due(source, NINE_AM.plusSeconds(4 * 3600))).isFalse();
	}

	@Test
	void aWeeklySourceWaitsAWeek() {
		JobSource source = new JobSource("linkedin");
		source.setFetchEveryHours(168);
		source.setLastRunAt(NINE_AM);

		assertThat(JobFetchOrchestrator.due(source, NINE_AM.plusSeconds(6 * 24 * 3600))).isFalse();
		assertThat(JobFetchOrchestrator.due(source, NINE_AM.plusSeconds(7 * 24 * 3600))).isTrue();
		assertThat(JobFetchOrchestrator.due(new JobSource("never-run"), NINE_AM)).isTrue();
	}

}
