package com.naukriradar.matching.scoring;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static com.naukriradar.matching.support.TestData.NOW;
import static com.naukriradar.matching.support.TestData.job;
import static com.naukriradar.matching.support.TestData.profile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class TitleFactorTest {

	private final TitleFactor factor = new TitleFactor();

	@ParameterizedTest(name = "{0} vs {1} -> {2}")
	@CsvSource(delimiter = '|', textBlock = """
			Backend Engineer       | Senior Backend Engineer (m/w/d)   | 1.0
			Backend Engineer       | backend engineer                  | 1.0
			Senior Java Developer  | Java Developer                    | 1.0
			Java Developer         | Java Software Engineer            | 0.5
			Backend Engineer       | Frontend Developer                | 0.0
			Data Engineer          | Big Data Platform Engineer        | 1.0
			""")
	void scoresHowCloseTheTitleIsToATargetRole(String role, String title, double expected) {
		double score = factor.score(MatchContext.of(profile().roles(role).build(), NOW), job().title(title).build()).score();

		assertThat(score).isCloseTo(expected, within(0.01));
	}

	@Test
	void theClosestOfSeveralRolesWins() {
		double score = factor.score(MatchContext.of(profile().roles("Frontend Developer", "DevOps Engineer").build(), NOW),
				job().title("Senior DevOps Engineer").build()).score();

		assertThat(score).isEqualTo(1.0);
	}

	@Test
	void noTargetRolesIsNeutral() {
		FactorResult result = factor.score(MatchContext.of(profile().roles().build(), NOW), job().build());

		assertThat(result.score()).isEqualTo(FactorResult.UNKNOWN);
		assertThat(result.detail()).contains("No target roles");
	}

}
