package com.naukriradar.matching.embedding;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class VectorsAndBm25Test {

	@Test
	void vectorsSurviveTheTripToBytesAndBack() {
		float[] vector = { 0.25f, -1.5f, 3.0f, 0f };

		assertThat(Vectors.fromBytes(Vectors.toBytes(vector))).containsExactly(vector);
		assertThat(Vectors.toBytes(vector)).hasSize(16);
		assertThatThrownBy(() -> Vectors.fromBytes(new byte[5])).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void cosineIsZeroForMissingOrMismatchedVectors() {
		assertThat(Vectors.cosine(new float[] { 1, 0 }, new float[] { 1, 0 })).isCloseTo(1, offset(1e-9));
		assertThat(Vectors.cosine(new float[] { 1, 0 }, new float[] { 0, 1 })).isZero();
		assertThat(Vectors.cosine(new float[] { 1, 0 }, new float[] { 1, 0, 0 })).isZero();
		assertThat(Vectors.cosine(null, new float[] { 1 })).isZero();
		assertThat(Vectors.cosine(new float[2], new float[] { 1, 1 })).isZero();
		assertThat(Vectors.normalize(new float[3])).containsOnly(0f);
		assertThat(Vectors.normalize(new float[] { 3, 4 })).containsExactly(0.6f, 0.8f);
	}

	@Test
	void hashesTellTextsApart() {
		assertThat(Vectors.hash("java")).hasSize(64).isEqualTo(Vectors.hash("java")).isNotEqualTo(Vectors.hash("Java"));
	}

	@Test
	void bm25PrefersThePassageWithTheRareQueryWord() {
		List<Double> scores = Bm25.scores("kafka experience", List.of(
				"Worked on Java services and SQL databases for payments.",
				"Built event pipelines on Kafka for order processing.",
				"Java and Spring Boot microservices with SQL."));

		assertThat(scores.get(1)).isGreaterThan(scores.get(0)).isGreaterThan(0);
		assertThat(scores.get(0)).isZero();
		assertThat(Bm25.scores("the and", List.of("anything"))).containsExactly(0.0);
		assertThat(Bm25.scores("kafka", List.of())).isEmpty();
	}

}
