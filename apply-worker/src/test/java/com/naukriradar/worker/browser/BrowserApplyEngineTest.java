package com.naukriradar.worker.browser;

import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import com.naukriradar.worker.browser.BrowserApplyEngine.FormResult;
import com.naukriradar.worker.browser.BrowserApplyEngine.Outcome;
import com.naukriradar.worker.config.BrowserProperties;
import com.naukriradar.worker.support.FakePortal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** A real headless Chrome against the local fake portal; no real site is ever touched. */
class BrowserApplyEngineTest {

	private static final FakePortal portal = startPortal();

	private final BrowserApplyEngine engine = new BrowserApplyEngine(new BrowserPool(properties()), properties());

	private final Map<String, String> answers = Map.of("fullName", "Asha Rao", "email", "asha@example.com");

	@BeforeEach
	void clear() {
		portal.submissions().clear();
	}

	@AfterAll
	static void stop() {
		portal.close();
	}

	@Test
	void theFormIsFilledSubmittedAndConfirmed() {
		FormResult result = engine.apply(portal.formUrl(), selectors(true), answers, true);

		assertThat(result.outcome()).isEqualTo(Outcome.SUBMITTED);
		assertThat(result.filled()).containsExactly("fullName", "email");
		assertThat(portal.submissions()).containsExactly("name=Asha Rao&email=asha@example.com");
	}

	@Test
	void aDryRunFillsButNeverSubmits() {
		FormResult result = engine.apply(portal.formUrl(), selectors(true), answers, false);

		assertThat(result.outcome()).isEqualTo(Outcome.DRY_RUN);
		assertThat(result.note()).contains("submit button found");
		assertThat(result.screenshot()).isNotEmpty();
		assertThat(portal.submissions()).isEmpty();
	}

	@Test
	void aFieldThatIsNotOnThePageMeansTheCandidateFinishesIt() {
		Map<String, String> selectors = new LinkedHashMap<>(selectors(true));
		selectors.put("phone", "#phone");

		FormResult result = engine.apply(portal.formUrl(), selectors,
				Map.of("fullName", "Asha Rao", "email", "asha@example.com", "phone", "+91 98765 43210"), true);

		assertThat(result.outcome()).isEqualTo(Outcome.NEEDS_YOU);
		assertThat(result.missing()).containsExactly("phone");
		assertThat(portal.submissions()).isEmpty();
	}

	@Test
	void withoutASuccessCheckItSaysUnknownRatherThanGuessing() {
		FormResult result = engine.apply(portal.formUrl(), selectors(false), answers, true);

		assertThat(result.outcome()).isEqualTo(Outcome.UNKNOWN);
		assertThat(portal.submissions()).hasSize(1);
	}

	@Test
	void noConfirmationMeansFailedWithAScreenshot() {
		Map<String, String> selectors = new LinkedHashMap<>(selectors(false));
		selectors.put("success", "#never-there");

		FormResult result = engine.apply(portal.formUrl(), selectors, answers, true);

		assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
		assertThat(result.screenshot()).isNotEmpty();
	}

	static Map<String, String> selectors(boolean withSuccess) {
		Map<String, String> selectors = new LinkedHashMap<>();
		selectors.put("fullName", "#name");
		selectors.put("email", "#email");
		selectors.put("submit", "#send");
		if (withSuccess) {
			selectors.put("success", "#thanks");
		}
		return selectors;
	}

	private static BrowserProperties properties() {
		return new BrowserProperties(1, true, Duration.ofSeconds(20), Duration.ofSeconds(2), Duration.ofSeconds(4), null,
				Path.of("build/test-screenshots"));
	}

	private static FakePortal startPortal() {
		try {
			return new FakePortal();
		}
		catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

}
