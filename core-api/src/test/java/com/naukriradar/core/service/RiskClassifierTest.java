package com.naukriradar.core.service;

import java.util.List;

import com.naukriradar.core.model.PortalConfig;
import com.naukriradar.core.model.RiskBand;
import com.naukriradar.core.settings.SettingDefinitions;
import com.naukriradar.core.support.FixedSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RiskClassifierTest {

	private final RiskClassifier classifier = new RiskClassifier(new FixedSettings()
			.with(SettingDefinitions.HIGH_RISK_DOMAINS, "linkedin.com, naukri.com, myworkdayjobs.com")
			.with(SettingDefinitions.LOW_RISK_DOMAINS, "greenhouse.io, lever.co"));

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({
			"https://www.linkedin.com/jobs/view/123, HIGH",
			"https://in.linkedin.com/jobs/view/123, HIGH",
			"https://www.naukri.com/job-listings-x, HIGH",
			"https://acme.wd1.myworkdayjobs.com/careers/job/1, HIGH",
			"https://boards.greenhouse.io/acme/jobs/1, LOW",
			"https://jobs.lever.co/acme/1, LOW",
			"HTTPS://JOBS.LEVER.CO/acme/1, LOW",
			"https://careers.acme.com/jobs/1, MEDIUM",
			"https://www.arbeitnow.com/jobs/acme/1, MEDIUM",
			"http://10.0.0.5/apply, MEDIUM" })
	void classifiesByDomain(String url, RiskBand expected) {
		assertThat(classifier.classify(url, List.of()).band()).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://linkedin.com.evil.io/jobs", "https://notlinkedin.com/jobs", "https://lever.co.phish.net/x" })
	void lookalikeDomainsAreNeitherTrustedNorMistakenForTheRealSite(String url) {
		assertThat(classifier.classify(url, List.of()).band()).isEqualTo(RiskBand.MEDIUM);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "not a url", "mailto:jobs@acme.com", "javascript:alert(1)", "/relative/apply" })
	void unusableLinksAreTreatedAsHighRisk(String url) {
		RiskClassifier.Risk risk = classifier.classify(url, List.of());

		assertThat(risk.band()).isEqualTo(RiskBand.HIGH);
		assertThat(risk.reason()).contains("isn't a usable web address");
	}

	@Test
	void adminPortalConfigWinsOverTheListsAndTheMostSpecificOneApplies() {
		PortalConfig careers = portal("careers.acme.com", RiskBand.LOW);
		PortalConfig acme = portal("acme.com", RiskBand.HIGH);
		PortalConfig linkedin = portal("linkedin.com", RiskBand.LOW);

		assertThat(classifier.classify("https://careers.acme.com/1", List.of(acme, careers)).band()).isEqualTo(RiskBand.LOW);
		assertThat(classifier.classify("https://shop.acme.com/1", List.of(acme, careers)).band()).isEqualTo(RiskBand.HIGH);
		assertThat(classifier.classify("https://www.linkedin.com/jobs/1", List.of(linkedin)).reason()).contains("by an admin");
	}

	@Test
	void reasonsExplainTheDecision() {
		assertThat(classifier.classify("https://www.linkedin.com/jobs/1", List.of()).reason())
				.isEqualTo("linkedin.com doesn't allow automated applications.");
	}

	private static PortalConfig portal(String domain, RiskBand risk) {
		PortalConfig portal = new PortalConfig(domain);
		portal.setName(domain);
		portal.setRiskBand(risk);
		portal.setEnabled(true);
		return portal;
	}

}
