package com.naukriradar.matching.scoring;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.config.MatchingProperties;
import org.junit.jupiter.api.Test;

import static com.naukriradar.matching.support.TestData.NOW;
import static com.naukriradar.matching.support.TestData.job;
import static com.naukriradar.matching.support.TestData.profile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MatchScorerTest {

	private static final Map<String, Integer> DEFAULT_WEIGHTS = Map.of("skills", 35, "title", 25, "location", 15,
			"salary", 10, "experience", 10, "recency", 5);

	@Test
	void totalIsTheWeightedAverageOutOf100() {
		MatchScorer scorer = scorer(List.of(fixed("a", 1.0), fixed("b", 0.0)), Map.of("a", 3, "b", 1));

		ScoreResult result = scorer.score(MatchContext.of(profile().build(), NOW), job().build());

		assertThat(result.total()).isEqualTo(75);
		assertThat(result.breakdown()).extracting(FactorScore::factor).containsExactly("a", "b");
		assertThat(result.breakdown().get(0).points()).isEqualTo(75.0);
	}

	@Test
	void aZeroWeightTurnsAFactorOff() {
		MatchScorer scorer = scorer(List.of(fixed("a", 1.0), fixed("b", 0.0)), Map.of("a", 1, "b", 0));

		assertThat(scorer.score(MatchContext.of(profile().build(), NOW), job().build()).total()).isEqualTo(100);
	}

	@Test
	void aPerfectJobScoresHighAndAPoorOneLow() {
		MatchScorer scorer = scorer(allFactors(), DEFAULT_WEIGHTS);
		var context = MatchContext.of(profile().build(), NOW);

		int good = scorer.score(context, job().salary(1_000_000L, 1_500_000L, "INR").build()).total();
		int poor = scorer.score(context, job().title("Sales Manager").location("Chennai").remote(false)
				.description("Cold calling, 10+ years of experience").postedAt(NOW.minusSeconds(90L * 86400)).build()).total();

		assertThat(good).isGreaterThanOrEqualTo(90);
		assertThat(poor).isLessThanOrEqualTo(20);
	}

	@Test
	void misconfiguredWeightsStopStartup() {
		List<ScoringFactor> factors = List.of(fixed("a", 1.0), fixed("b", 1.0));

		assertThatThrownBy(() -> scorer(factors, Map.of("a", 1))).hasMessageContaining("weights.b");
		assertThatThrownBy(() -> scorer(factors, Map.of("a", 1, "b", 1, "typo", 1))).hasMessageContaining("typo");
		assertThatThrownBy(() -> scorer(factors, Map.of("a", -1, "b", 1))).hasMessageContaining("weights.a");
		assertThatThrownBy(() -> scorer(factors, Map.of("a", 0, "b", 0))).hasMessageContaining("add up to 0");
	}

	private static MatchScorer scorer(List<ScoringFactor> factors, Map<String, Integer> weights) {
		return new MatchScorer(factors, new MatchingProperties(new HashMap<>(weights), 20, 300, 60, 2, 20, 10));
	}

	static List<ScoringFactor> allFactors() {
		return List.of(new SkillFactor(), new TitleFactor(), new LocationFactor(), new SalaryFactor(),
				new ExperienceFactor(), new RecencyFactor());
	}

	static Map<String, Integer> defaultWeights() {
		return DEFAULT_WEIGHTS;
	}

	private static ScoringFactor fixed(String name, double score) {
		return new ScoringFactor() {

			@Override
			public String name() {
				return name;
			}

			@Override
			public FactorResult score(MatchContext context, CandidateJob job) {
				return new FactorResult(score, name);
			}

		};
	}

}
