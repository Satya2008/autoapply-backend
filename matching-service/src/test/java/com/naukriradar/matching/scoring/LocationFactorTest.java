package com.naukriradar.matching.scoring;

import com.naukriradar.matching.support.TestData.JobBuilder;
import com.naukriradar.matching.support.TestData.ProfileBuilder;
import org.junit.jupiter.api.Test;

import static com.naukriradar.matching.support.TestData.NOW;
import static com.naukriradar.matching.support.TestData.job;
import static com.naukriradar.matching.support.TestData.profile;
import static org.assertj.core.api.Assertions.assertThat;

class LocationFactorTest {

	private final LocationFactor factor = new LocationFactor();

	@Test
	void remoteJobForSomeoneOpenToRemoteIsPerfect() {
		assertThat(score(profile().remoteOk(true).locations(), job().remote(true).location("Berlin")))
				.isEqualTo(1.0);
	}

	@Test
	void jobInAPreferredCityIsPerfect() {
		assertThat(score(profile().locations("Pune", "Bengaluru"), job().location("Bengaluru, Karnataka"))).isEqualTo(1.0);
	}

	@Test
	void cityMatchIsWholeWord() {
		assertThat(score(profile().locations("Pune").remoteOk(false), job().location("Punekar Nagar, Mumbai"))).isZero();
	}

	@Test
	void remoteJobForSomeoneWhoDidntOptInScoresLow() {
		assertThat(score(profile().remoteOk(false).locations("Pune"), job().remote(true).location("Anywhere")))
				.isEqualTo(0.3);
	}

	@Test
	void noPreferredLocationsOrNoJobLocationIsNeutral() {
		assertThat(score(profile().locations().remoteOk(false), job().location("Delhi"))).isEqualTo(FactorResult.UNKNOWN);
		assertThat(score(profile().locations("Pune").remoteOk(false), job().location(null))).isEqualTo(FactorResult.UNKNOWN);
	}

	@Test
	void otherCityScoresZero() {
		assertThat(score(profile().locations("Pune").remoteOk(false), job().location("Chennai"))).isZero();
	}

	private double score(ProfileBuilder profile, JobBuilder job) {
		return factor.score(MatchContext.of(profile.build(), NOW), job.build()).score();
	}

}
