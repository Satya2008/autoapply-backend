package com.naukriradar.matching.scoring;

import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.client.MatchingProfile;
import org.junit.jupiter.api.Test;

import static com.naukriradar.matching.support.TestData.NOW;
import static com.naukriradar.matching.support.TestData.job;
import static com.naukriradar.matching.support.TestData.profile;
import static org.assertj.core.api.Assertions.assertThat;

class SkillFactorTest {

	private final SkillFactor factor = new SkillFactor();

	@Test
	void allSkillsMentionedIsAFullScore() {
		FactorResult result = score(profile().skills("java", "spring boot", "mysql").build(),
				"Java, Spring Boot and MySQL");

		assertThat(result.score()).isEqualTo(1.0);
		assertThat(result.detail()).contains("3 of your skills").contains("spring boot");
	}

	@Test
	void manySkillsAreMeasuredAgainstAHandfulNotAll() {
		String[] skills = { "java", "kafka", "redis", "docker", "aws", "react", "angular", "python", "go", "rust" };
		FactorResult result = score(profile().skills(skills).build(), "Java, Kafka and Redis");

		assertThat(result.score()).isEqualTo(3.0 / SkillFactor.FULL_MATCH_SKILLS);
	}

	@Test
	void javaIsNotFoundInsideJavascript() {
		assertThat(score(profile().skills("java").build(), "Strong JavaScript skills").score()).isZero();
	}

	@Test
	void symbolSkillsMatchExactly() {
		assertThat(score(profile().skills("c#", ".net").build(), "C# and .NET Core").score()).isEqualTo(1.0);
		assertThat(score(profile().skills("c").build(), "C++ developer").score()).isZero();
	}

	@Test
	void skillsInTheTitleCount() {
		assertThat(score(profile().skills("kotlin").build(), job().title("Kotlin Developer").description(null).build())
				.score()).isEqualTo(1.0);
	}

	@Test
	void noSkillsOnProfileScoresZeroAndSaysWhy() {
		FactorResult result = score(profile().skills().build(), "Java");

		assertThat(result.score()).isZero();
		assertThat(result.detail()).contains("No skills");
	}

	private FactorResult score(MatchingProfile profile, String description) {
		return score(profile, job().title("Engineer").description(description).build());
	}

	private FactorResult score(MatchingProfile profile, CandidateJob job) {
		return factor.score(MatchContext.of(profile, NOW), job);
	}

}
