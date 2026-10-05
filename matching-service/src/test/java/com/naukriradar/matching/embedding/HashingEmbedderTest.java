package com.naukriradar.matching.embedding;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class HashingEmbedderTest {

	private final HashingEmbedder embedder = new HashingEmbedder(JsonMapper.builder().build());

	@Test
	void theSameJobInOtherWordsIsCloserThanAJobThatOnlyLooksAlike() {
		float[] profile = embedder.embed("Roles: Java Backend Developer\nSkills: java, spring boot, mysql");
		float[] sameMeaning = embedder.embed("Role: Spring Microservices Engineer\nAbout: REST APIs with Spring Cloud and Kafka");
		float[] lookAlike = embedder.embed("Role: JavaScript Developer\nAbout: React, TypeScript, CSS and HTML");

		assertThat(Vectors.cosine(profile, sameMeaning)).isGreaterThan(Vectors.cosine(profile, lookAlike) + 0.1);
	}

	@Test
	void unrelatedTextsAreFarApartAndATextIsClosestToItself() {
		float[] java = embedder.embed("Senior Java developer, Spring Boot and Kafka");
		float[] sales = embedder.embed("Inside sales executive, cold calling, monthly targets");

		assertThat(Vectors.cosine(java, java)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
		assertThat(Vectors.cosine(java, sales)).isLessThan(0.1);
	}

	@Test
	void javaIsNotFoundInsideJavascriptButNodeJsIsFoundWhole() {
		assertThat(HashingEmbedder.containsWord("we use javascript daily", "java")).isFalse();
		assertThat(HashingEmbedder.containsWord("we use java.", "java")).isTrue();
		assertThat(HashingEmbedder.containsWord("apis in node.js and go", "node.js")).isTrue();
		assertThat(HashingEmbedder.containsWord("c++ and c#", "c")).isFalse();
	}

	@Test
	void vectorsAreUnitLengthDeterministicAndEmptyTextGivesZero() {
		float[] first = embedder.embed("Data engineer with Spark and Airflow");
		float[] second = embedder.embed("Data engineer with Spark and Airflow");

		assertThat(first).hasSize(HashingEmbedder.DIMENSIONS).containsExactly(second);
		double norm = 0;
		for (float v : first) {
			norm += v * v;
		}
		assertThat(norm).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-5));
		assertThat(embedder.embed("  ")).containsOnly(0f);
		assertThat(embedder.embed("the and of")).containsOnly(0f);
	}

	@Test
	void theSkillVocabularyLeavesOutRoleWordsAndBroadAreas() {
		assertThat(embedder.skillVocabulary()).contains("kafka", "docker", "spring boot", "react")
				.doesNotContain("engineer", "developer", "backend", "cloud", "manager");
	}

}
