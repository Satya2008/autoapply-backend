package com.naukriradar.core.skill;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs against the real dictionary shipped with the service. */
class SkillExtractorTest {

	private final SkillExtractor extractor =
			new SkillExtractor(SkillDictionary.load(new ClassPathResource("skills/dictionary.txt")));

	@Test
	void javaDoesNotMatchInsideJavascript() {
		assertThat(extractor.extract("Built dashboards in JavaScript and TypeScript."))
				.containsExactly("javascript", "typescript")
				.doesNotContain("java");
	}

	@Test
	void javaAndJavascriptAreBothFoundWhenBothArePresent() {
		assertThat(extractor.extract("Java, JavaScript")).containsExactly("java", "javascript");
	}

	@ParameterizedTest
	@ValueSource(strings = { "Spring Boot", "spring-boot", "SpringBoot", "springboot", "Spring  Boot", "spring_boot" })
	void springBootIsFoundHoweverItIsWritten(String text) {
		assertThat(extractor.extract("Worked with " + text + " daily.")).containsExactly("spring boot");
	}

	@Test
	void aliasesMapToTheCanonicalName() {
		assertThat(extractor.extract("JS, k8s, Postgres, golang"))
				.containsExactly("javascript", "kubernetes", "postgresql", "golang");
	}

	@Test
	void symbolSkillsAreMatchedExactly() {
		assertThat(extractor.extract("C++, C# and .NET; also ASP.NET and Node.js"))
				.containsExactly("c++", "c#", ".net", "asp.net", "node.js");
	}

	@Test
	void longerSkillWinsOverTheShorterOneInsideIt() {
		assertThat(extractor.extract("Spring Data JPA")).containsExactly("spring data jpa");
		assertThat(extractor.extract("React Native")).containsExactly("react native");
	}

	@Test
	void wordsInsideOtherWordsAreIgnored() {
		// sql in mysql, git in github, java in javadoc, scala in scalable
		assertThat(extractor.extract("MySQL on GitHub, javadoc, scalable systems"))
				.containsExactly("mysql", "github");
	}

	@Test
	void punctuationAroundASkillStillCounts() {
		assertThat(extractor.extract("(Java). Kafka, Redis; Docker/Kubernetes"))
				.containsExactly("java", "kafka", "redis", "docker", "kubernetes");
	}

	@Test
	void commonEnglishWordsAreNotSkills() {
		assertThat(extractor.extract("I want to go and express my interest in this node of work.")).isEmpty();
	}

	@Test
	void eachSkillIsReportedOnceInFirstSeenOrder() {
		assertThat(extractor.extract("Docker, Java, docker, JAVA, Docker"))
				.containsExactly("docker", "java");
	}

	@Test
	void emptyOrMissingTextFindsNothing() {
		assertThat(extractor.extract(null)).isEmpty();
		assertThat(extractor.extract("   ")).isEmpty();
	}

}
