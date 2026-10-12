package com.naukriradar.job.provider;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RobotsTxtPolicyTest {

	@Test
	void theStarGroupAppliesWhenNoGroupNamesUs() {
		RobotsTxtPolicy.Rules rules = RobotsTxtPolicy.parse("""
				User-agent: Googlebot
				Disallow:

				User-agent: *
				Disallow: /search
				Allow: /search/jobs
				""", "naukriradar");

		assertThat(rules.allows("/careers")).isTrue();
		assertThat(rules.allows("/search?q=java")).isFalse();
		// the longer rule wins
		assertThat(rules.allows("/search/jobs?page=2")).isTrue();
	}

	@Test
	void ourOwnGroupBeatsTheStarGroup() {
		RobotsTxtPolicy.Rules rules = RobotsTxtPolicy.parse("""
				User-agent: *
				Disallow: /

				User-agent: NaukriRadar
				Allow: /jobs
				Disallow: /
				""", "naukriradar");

		assertThat(rules.allows("/jobs/backend")).isTrue();
		assertThat(rules.allows("/about")).isFalse();
	}

	@Test
	void wildcardsAndEndAnchorsWork() {
		RobotsTxtPolicy.Rules rules = RobotsTxtPolicy.parse("""
				User-agent: *
				Disallow: /*.pdf$
				Disallow: /*?sessionid=
				""", "naukriradar");

		assertThat(rules.allows("/files/jd.pdf")).isFalse();
		assertThat(rules.allows("/files/jd.pdf?x=1")).isTrue();
		assertThat(rules.allows("/jobs?sessionid=abc")).isFalse();
	}

	@Test
	void anEmptyOrMissingFileAllowsEverything() {
		assertThat(RobotsTxtPolicy.parse("", "naukriradar").allows("/anything")).isTrue();
		assertThat(RobotsTxtPolicy.parse("User-agent: *\nDisallow:\n", "naukriradar").allows("/anything")).isTrue();
	}

}
