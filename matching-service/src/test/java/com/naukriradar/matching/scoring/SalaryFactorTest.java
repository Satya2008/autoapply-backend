package com.naukriradar.matching.scoring;

import org.junit.jupiter.api.Test;

import static com.naukriradar.matching.support.TestData.NOW;
import static com.naukriradar.matching.support.TestData.job;
import static com.naukriradar.matching.support.TestData.profile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class SalaryFactorTest {

	private final SalaryFactor factor = new SalaryFactor();

	@Test
	void payingAtLeastTheExpectationIsPerfect() {
		FactorResult result = score(1_200_000L, 1_000_000L, 1_500_000L, "INR");

		assertThat(result.score()).isEqualTo(1.0);
		assertThat(result.detail()).contains("₹15,00,000");
	}

	@Test
	void onlyAMinimumIsUsedAsTheTop() {
		assertThat(score(1_000_000L, 1_000_000L, null, "INR").score()).isEqualTo(1.0);
	}

	@Test
	void fallingShortLowersTheScoreSteeply() {
		assertThat(score(1_000_000L, null, 750_000L, "INR").score()).isCloseTo(0.5, within(0.001));
		assertThat(score(1_000_000L, null, 400_000L, "INR").score()).isZero();
	}

	@Test
	void missingOrUncomparableSalariesAreNeutral() {
		assertThat(score(null, 1_000_000L, 2_000_000L, "INR").score()).isEqualTo(FactorResult.UNKNOWN);
		assertThat(score(1_000_000L, null, null, null).score()).isEqualTo(FactorResult.UNKNOWN);
		assertThat(score(1_000_000L, 60_000L, 80_000L, "EUR").detail()).contains("EUR");
		assertThat(score(1_000_000L, 60_000L, 80_000L, null).score()).isEqualTo(FactorResult.UNKNOWN);
	}

	private FactorResult score(Long expected, Long min, Long max, String currency) {
		return factor.score(MatchContext.of(profile().expectedSalary(expected).build(), NOW),
				job().salary(min, max, currency).build());
	}

}
