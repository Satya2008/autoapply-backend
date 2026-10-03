package com.naukriradar.matching.scoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.naukriradar.matching.support.TestData.NOW;
import static com.naukriradar.matching.support.TestData.job;
import static com.naukriradar.matching.support.TestData.profile;
import static org.assertj.core.api.Assertions.assertThat;

class ExperienceFactorTest {

	private final ExperienceFactor factor = new ExperienceFactor();

	@ParameterizedTest(name = "{0} | {1}")
	@CsvSource(delimiter = '|', nullValues = "NULL", textBlock = """
			Engineer          | We want 3-5 years of experience.             | 3 | 5
			Engineer          | 2 to 4 yrs in backend                        | 2 | 4
			Engineer          | 5+ years of experience with Java             | 5 | NULL
			Engineer          | Minimum 4 years of relevant experience       | 4 | NULL
			Engineer          | at least 2 years' professional experience    | 2 | NULL
			Senior Engineer   | No numbers here                              | 5 | NULL
			Junior Developer  | No numbers here                              | 0 | 2
			Software Intern   | No numbers here                              | 0 | 1
			Engineering Lead  | No numbers here                              | 6 | NULL
			Engineer          | Founded in 1998, 25 years of history         | NULL | NULL
			Engineer          | Serving 100 years of clients                 | NULL | NULL
			""")
	void readsWhatThePostingAsksFor(String title, String description, Integer min, Integer max) {
		ExperienceParser.Range range = ExperienceParser.parse(title, description);

		if (min == null) {
			assertThat(range).isNull();
		}
		else {
			assertThat(range).isEqualTo(new ExperienceParser.Range(min, max));
		}
	}

	@ParameterizedTest(name = "has {0}, asked 3-5 -> {1}")
	@CsvSource({ "4, 1.0", "3, 1.0", "5, 1.0", "2, 0.6", "1, 0.3", "0, 0.1", "7, 0.8", "9, 0.5" })
	void fallingShortHurtsMoreThanBeingOver(int years, double expected) {
		double score = factor.score(MatchContext.of(profile().experience(years).build(), NOW),
				job().description("3-5 years of experience").build()).score();

		assertThat(score).isEqualTo(expected);
	}

	@Test
	void unknownOnEitherSideIsNeutral() {
		assertThat(factor.score(MatchContext.of(profile().experience(null).build(), NOW), job().build()).score())
				.isEqualTo(FactorResult.UNKNOWN);
		assertThat(factor.score(MatchContext.of(profile().experience(3).build(), NOW),
				job().title("Engineer").description("Great team").build()).score())
				.isEqualTo(FactorResult.UNKNOWN);
	}

}
