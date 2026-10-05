package com.naukriradar.core.service;

import java.util.List;

import com.naukriradar.core.skill.SkillDictionary;
import com.naukriradar.core.skill.SkillExtractor;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class HallucinationGuardTest {

	private static final String RESUME = """
			Backend developer at Acme Corp, 2021-2024.
			Built payment services in Java and Kafka for 2 million users; cut latency by 35%.
			Skills: Java, Spring Boot, MySQL. 4 years of experience.""";

	private final HallucinationGuard guard = new HallucinationGuard(
			new SkillExtractor(SkillDictionary.load(new ClassPathResource("skills/dictionary.txt"))));

	@Test
	void aLetterThatOnlyRepeatsTheResumeIsGrounded() {
		HallucinationGuard.Check check = guard.check("""
				At Acme Corp I built payment services in Java and Kafka for 2 million users and cut latency by 35%.
				With 4 years of Spring Boot behind me, I'd love to join PayFlow.""", RESUME, List.of("PayFlow", "Payments Engineer"));

		assertThat(check.grounded()).isTrue();
	}

	@Test
	void inventedSkillsNumbersAndEmployersAreFlagged() {
		HallucinationGuard.Check check = guard.check(
				"I spent 9 years running Kubernetes at Google and grew revenue by 300%.", RESUME, List.of("PayFlow"));

		assertThat(check.grounded()).isFalse();
		assertThat(check.unsupported()).anyMatch(c -> c.contains("kubernetes"))
				.anyMatch(c -> c.contains("9 years"))
				.anyMatch(c -> c.contains("300%"))
				.anyMatch(c -> c.contains("Google"));
	}

	@Test
	void theJobsOwnNamesAndSkillsMayBeMentioned() {
		HallucinationGuard.Check check = guard.check("I would love to work at PayFlow on your Go services.", RESUME,
				List.of("PayFlow", "Build services in Go"));

		assertThat(check.unsupported()).isEmpty();
		assertThat(guard.check("", RESUME, List.of()).grounded()).isTrue();
	}

}
