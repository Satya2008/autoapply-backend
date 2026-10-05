package com.naukriradar.matching.service;

import java.util.List;

import com.naukriradar.matching.client.CandidateJob;
import org.junit.jupiter.api.Test;

import static com.naukriradar.matching.support.TestData.job;
import static org.assertj.core.api.Assertions.assertThat;

class SemanticCandidateFinderTest {

	@Test
	void aJobBothListsLikeRisesToTheTop() {
		CandidateJob a = job().id("a").build();
		CandidateJob b = job().id("b").build();
		CandidateJob c = job().id("c").build();
		CandidateJob d = job().id("d").build();

		List<CandidateJob> fused = SemanticCandidateFinder.fuse(List.of(a, b, c), List.of(d, c), 10);

		// c: 1/63 + 1/62 beats a: 1/61 alone
		assertThat(fused).extracting(CandidateJob::id).containsExactly("c", "a", "d", "b");
	}

	@Test
	void fusionKeepsEachJobOnceAndRespectsTheLimit() {
		CandidateJob a = job().id("a").build();
		CandidateJob b = job().id("b").build();

		assertThat(SemanticCandidateFinder.fuse(List.of(a, a, b), List.of(), 10)).extracting(CandidateJob::id)
				.containsExactly("a", "b");
		assertThat(SemanticCandidateFinder.fuse(List.of(a, b), List.of(b), 1)).extracting(CandidateJob::id)
				.containsExactly("b");
		assertThat(SemanticCandidateFinder.fuse(List.of(), List.of(), 5)).isEmpty();
	}

}
