package com.naukriradar.worker.browser;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import com.naukriradar.worker.browser.FormReviewer.ReviewResult;
import com.naukriradar.worker.config.BrowserProperties;
import com.naukriradar.worker.support.FakePortal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** "Fill for me" against a local career site, in a real headless Chrome. */
class FillFormTest {

	private static final FakePortal portal = start();

	private static final BrowserProperties properties = new BrowserProperties(1, true, Duration.ofSeconds(20),
			Duration.ofSeconds(2), Duration.ofSeconds(4), null, Path.of("build/test-screenshots"), Path.of("build/test-profile"),
			9333, false);

	private final BrowserPool pool = new BrowserPool(properties);

	private final FormReviewer reviewer = new FormReviewer(pool, new BrowserApplyEngine(pool, properties), properties);

	private final Map<String, String> answers = Map.of("firstName", "Asha", "email", "asha@example.com");

	@BeforeEach
	void clear() {
		portal.submissions().clear();
		portal.cookieChoices().clear();
	}

	@AfterAll
	static void stop() {
		portal.close();
	}

	@Test
	void turnsDownTheCookieBannerFollowsApplyToTheNewTabAndFillsButNeverSubmits() {
		ReviewResult result = pool.withBrowser(driver ->
				reviewer.fillForm(driver, portal.careerUrl(false), Map.of(), answers, questions -> Map.of()));

		assertThat(result.filled()).contains("First Name", "Email Address");
		assertThat(result.leftForYou()).isEmpty();
		assertThat(result.note()).isEqualTo("Filled. Check it and press Submit.");
		assertThat(portal.cookieChoices()).containsExactly("choice=reject");
		assertThat(portal.submissions()).isEmpty();
	}

	@Test
	void aQuestionOnlyTheCandidateCanAnswerIsLeftForThem() {
		ReviewResult result = pool.withBrowser(driver ->
				reviewer.fillForm(driver, portal.careerUrl(true), Map.of(), answers, questions -> Map.of()));

		assertThat(result.leftForYou()).containsExactly("Gender");
		assertThat(portal.submissions()).isEmpty();
	}

	@Test
	void theProfilesAnswerBankAnswersTheUsualExtraQuestion() {
		Map<String, String> withBank = Map.of("firstName", "Asha", "email", "asha@example.com", "gender", "I'd prefer not to say");

		ReviewResult result = pool.withBrowser(driver ->
				reviewer.fillForm(driver, portal.careerUrl(true), Map.of(), withBank, questions -> Map.of()));

		assertThat(result.filled()).contains("Gender");
		assertThat(result.leftForYou()).isEmpty();
	}

	private static FakePortal start() {
		try {
			return new FakePortal();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
