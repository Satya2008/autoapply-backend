package com.naukriradar.matching.scoring;

import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.naukriradar.matching.support.TestData.NOW;
import static com.naukriradar.matching.support.TestData.job;
import static com.naukriradar.matching.support.TestData.profile;
import static org.assertj.core.api.Assertions.assertThat;

class RecencyFactorTest {

	private final RecencyFactor factor = new RecencyFactor();

	@ParameterizedTest(name = "{0} days old -> {1}")
	@CsvSource({ "0, 1.0", "1, 1.0", "3, 0.9", "6, 0.75", "10, 0.55", "20, 0.35", "45, 0.15" })
	void newerIsBetter(int days, double expected) {
		assertThat(score(days)).isEqualTo(expected);
	}

	@Test
	void futureDatesFromClockSkewCountAsNew() {
		assertThat(factor.score(MatchContext.of(profile().build(), NOW), job().postedAt(NOW.plusSeconds(600)).build()).score())
				.isEqualTo(1.0);
	}

	@Test
	void unknownDateIsNeutral() {
		assertThat(factor.score(MatchContext.of(profile().build(), NOW), job().postedAt(null).build()).score())
				.isEqualTo(FactorResult.UNKNOWN);
	}

	private double score(int daysOld) {
		return factor.score(MatchContext.of(profile().build(), NOW),
				job().postedAt(NOW.minus(daysOld, ChronoUnit.DAYS)).build()).score();
	}

}
