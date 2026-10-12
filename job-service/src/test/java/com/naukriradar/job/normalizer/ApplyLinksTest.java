package com.naukriradar.job.normalizer;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ApplyLinksTest {

	private static final String LINKEDIN = "https://in.linkedin.com/jobs/view/java-developer-123";

	@Test
	void prefersAnApplicationSystemThenTheCompanySiteThenABoardOverLinkedIn() {
		assertThat(ApplyLinks.best(LINKEDIN, List.of("https://www.shine.com/jobs/1", "https://careers.acme.com/jobs/1",
				"https://boards.greenhouse.io/acme/jobs/1"))).isEqualTo("https://boards.greenhouse.io/acme/jobs/1");
		assertThat(ApplyLinks.best(LINKEDIN, List.of("https://www.shine.com/jobs/1", "https://careers.acme.com/jobs/1")))
				.isEqualTo("https://careers.acme.com/jobs/1");
		assertThat(ApplyLinks.best(LINKEDIN, List.of("https://www.shine.com/jobs/1"))).isEqualTo("https://www.shine.com/jobs/1");
	}

	@Test
	void keepsTheMainLinkWhenTheOptionsAreNoBetterOrNotLinks() {
		assertThat(ApplyLinks.best("https://jobs.lever.co/acme/1", List.of(LINKEDIN))).isEqualTo("https://jobs.lever.co/acme/1");
		assertThat(ApplyLinks.best(LINKEDIN, Arrays.asList(null, "javascript:alert(1)", "not a link"))).isEqualTo(LINKEDIN);
		assertThat(ApplyLinks.best(LINKEDIN, null)).isEqualTo(LINKEDIN);
	}

	@Test
	void anOptionCanStandInForAMissingMainLink() {
		assertThat(ApplyLinks.best(null, List.of("https://careers.acme.com/jobs/1"))).isEqualTo("https://careers.acme.com/jobs/1");
	}

}
