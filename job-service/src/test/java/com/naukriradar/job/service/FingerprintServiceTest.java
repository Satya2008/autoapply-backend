package com.naukriradar.job.service;

import com.naukriradar.job.normalizer.NormalizedJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class FingerprintServiceTest {

	private final FingerprintService fingerprints = new FingerprintService();

	@ParameterizedTest(name = "{0} | {1} | {2}")
	@CsvSource(delimiter = '|', textBlock = """
			Java Developer (m/w/d)        | Acme GmbH          | Berlin, Germany
			java developer                | ACME               | berlin
			Java-Developer (f/m/x)        | Acme Gmbh & Co. KG | Berlin
			Java Developer (all genders)  | Acme AG            | Berlin / Remote
			  Java   Developer            | Acme Ltd.          | BERLIN
			""")
	void sameJobOnDifferentBoardsGetsTheSameFingerprint(String title, String company, String location) {
		assertThat(fingerprints.fingerprint(job(title, company, location, false)))
				.isEqualTo(fingerprints.fingerprint(job("Java Developer", "Acme", "Berlin", false)));
	}

	@Test
	void accentsDontMatter() {
		assertThat(fingerprints.fingerprint(job("Entwickler", "Müller Café", "München", false)))
				.isEqualTo(fingerprints.fingerprint(job("Entwickler", "Muller Cafe", "Munchen", false)));
	}

	@Test
	void indianCompanySuffixesAreIgnored() {
		assertThat(fingerprints.fingerprint(job("Backend Engineer", "Infosys Private Limited", "Pune", false)))
				.isEqualTo(fingerprints.fingerprint(job("Backend Engineer", "Infosys Pvt Ltd", "Pune, Maharashtra", false)));
	}

	@Test
	void differentCityTitleOrCompanyIsADifferentJob() {
		String base = fingerprints.fingerprint(job("Java Developer", "Acme", "Berlin", false));

		assertThat(fingerprints.fingerprint(job("Java Developer", "Acme", "Munich", false))).isNotEqualTo(base);
		assertThat(fingerprints.fingerprint(job("Senior Java Developer", "Acme", "Berlin", false))).isNotEqualTo(base);
		assertThat(fingerprints.fingerprint(job("Java Developer", "Globex", "Berlin", false))).isNotEqualTo(base);
	}

	@Test
	void remoteJobsWithoutALocationShareTheRemoteCity() {
		assertThat(FingerprintService.city(job("Dev", "Acme", null, true))).isEqualTo("remote");
		assertThat(FingerprintService.city(job("Dev", "Acme", "  ", false))).isEmpty();
	}

	@Test
	void fingerprintIsAHexSha256() {
		assertThat(fingerprints.fingerprint(job("Dev", "Acme", "Pune", false))).matches("[0-9a-f]{64}");
	}

	private static NormalizedJob job(String title, String company, String location, boolean remote) {
		return new NormalizedJob("x", title, company, location, remote, null, null, null, null,
				"https://jobs.example.com/x", null);
	}

}
