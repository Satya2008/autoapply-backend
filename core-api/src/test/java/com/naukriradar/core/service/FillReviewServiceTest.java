package com.naukriradar.core.service;

import java.util.List;
import java.util.Map;

import com.naukriradar.core.dto.response.FillReviewResponse;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FillReviewServiceTest {

	@Test
	void saysOnlySubmitIsLeftWhenEverythingIsFilled() {
		FillReviewResponse done = new FillReviewResponse(true, List.of("email", "phone"), Map.of(), List.of(), null);

		assertThat(FillReviewService.readyReason(done))
				.isEqualTo("Ready in Chrome: everything is filled; only Submit is left.");
	}

	@Test
	void whenNothingCouldBeFilledTheWorkersExplanationIsTheReason() {
		FillReviewResponse login = new FillReviewResponse(true, List.of(), Map.of(), List.of("Log in on this site"),
				"This site wants you to log in first.");

		assertThat(FillReviewService.readyReason(login)).isEqualTo("This site wants you to log in first.");
	}

	@Test
	void countsAiAnswersAsFilledAndNamesWhatIsLeft() {
		FillReviewResponse partly = new FillReviewResponse(true, List.of("email"), Map.of("Why us?", "..."),
				List.of("Visa status", "Gender"), null);

		assertThat(FillReviewService.readyReason(partly))
				.isEqualTo("Ready in Chrome: filled 2 fields; still to answer: Visa status, Gender.");
	}

}
