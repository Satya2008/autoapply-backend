package com.naukriradar.matching.scoring;

import java.util.Map;

import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.config.SemanticProperties;
import org.junit.jupiter.api.Test;

import static com.naukriradar.matching.support.TestData.NOW;
import static com.naukriradar.matching.support.TestData.job;
import static com.naukriradar.matching.support.TestData.profile;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class SemanticFactorTest {

	private final SemanticFactor factor = new SemanticFactor();

	private final SemanticProperties.Range range = new SemanticProperties.Range(0.2, 0.6);

	@Test
	void cosineIsMappedOntoTheModelsRange() {
		CandidateJob job = job().build();
		float[] profile = { 1, 0 };

		assertThat(score(profile, Map.of(job.id(), new float[] { 1, 0 }), job)).isEqualTo(1.0);
		assertThat(score(profile, Map.of(job.id(), new float[] { 0, 1 }), job)).isZero();
		// cosine 0.4 is halfway between 0.2 and 0.6
		assertThat(score(profile, Map.of(job.id(), new float[] { 0.4f, (float) Math.sqrt(1 - 0.16) }), job))
				.isCloseTo(0.5, offset(1e-3));
	}

	@Test
	void withoutVectorsItCantTell() {
		CandidateJob job = job().build();

		assertThat(factor.score(MatchContext.of(profile().build(), NOW), job).score()).isEqualTo(FactorResult.UNKNOWN);
		assertThat(score(new float[] { 1, 0 }, Map.of(), job)).isEqualTo(FactorResult.UNKNOWN);
	}

	@Test
	void aRangeMustGoUp() {
		assertThatThrownBy(() -> new SemanticProperties.Range(0.5, 0.5)).isInstanceOf(IllegalArgumentException.class);
	}

	private double score(float[] profile, Map<String, float[]> jobs, CandidateJob job) {
		MatchContext context = MatchContext.of(profile().build(), NOW, new SemanticVectors("test", profile, jobs, range));
		return factor.score(context, job).score();
	}

}
