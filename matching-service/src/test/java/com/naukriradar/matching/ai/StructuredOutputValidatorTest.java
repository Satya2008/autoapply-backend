package com.naukriradar.matching.ai;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredOutputValidatorTest {

	private static final Map<String, Object> JOB_FIT = Map.of(
			"type", "object",
			"required", List.of("score", "reasons"),
			"properties", Map.of(
					"score", Map.of("type", "integer", "minimum", 0, "maximum", 100),
					"reasons", Map.of("type", "array", "items", Map.of("type", "string", "maxLength", 10)),
					"verdict", Map.of("type", "string", "enum", List.of("fit", "no-fit"))));

	private final StructuredOutputValidator validator = new StructuredOutputValidator(JsonMapper.builder().build());

	@Test
	void aGoodAnswerPassesUnchanged() {
		JsonNode answer = validator.validate("{\"score\": 72, \"reasons\": [\"java\"], \"verdict\": \"fit\"}", JOB_FIT);

		assertThat(answer.get("score").asInt()).isEqualTo(72);
		assertThat(answer.get("reasons").get(0).asString()).isEqualTo("java");
	}

	@Test
	void fencesAndChatterAroundTheJsonAreIgnored() {
		JsonNode answer = validator.validate("Here you go:\n```json\n{\"score\": 5, \"reasons\": []}\n```", JOB_FIT);

		assertThat(answer.get("score").asInt()).isEqualTo(5);
	}

	@Test
	void outOfRangeNumbersAreClampedAndLongStringsCut() {
		JsonNode high = validator.validate("{\"score\": 140, \"reasons\": [\"a very long reason\"]}", JOB_FIT);
		JsonNode low = validator.validate("{\"score\": -3, \"reasons\": []}", JOB_FIT);

		assertThat(high.get("score").asInt()).isEqualTo(100);
		assertThat(high.get("reasons").get(0).asString()).isEqualTo("a very lon");
		assertThat(low.get("score").asInt()).isZero();
	}

	@Test
	void wrongShapesAreRejected() {
		assertThatThrownBy(() -> validator.validate("not json", JOB_FIT)).isInstanceOf(InvalidAiOutputException.class)
				.hasMessageContaining("not valid JSON");
		assertThatThrownBy(() -> validator.validate("{\"reasons\": []}", JOB_FIT)).hasMessageContaining("score is missing");
		assertThatThrownBy(() -> validator.validate("{\"score\": \"high\", \"reasons\": []}", JOB_FIT))
				.hasMessageContaining("must be an integer");
		assertThatThrownBy(() -> validator.validate("{\"score\": 7.5, \"reasons\": []}", JOB_FIT))
				.hasMessageContaining("must be an integer");
		assertThatThrownBy(() -> validator.validate("{\"score\": 1, \"reasons\": \"java\"}", JOB_FIT))
				.hasMessageContaining("must be an array");
		assertThatThrownBy(() -> validator.validate("{\"score\": 1, \"reasons\": [], \"verdict\": \"maybe\"}", JOB_FIT))
				.hasMessageContaining("must be one of");
	}

}
