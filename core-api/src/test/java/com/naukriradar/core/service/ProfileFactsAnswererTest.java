package com.naukriradar.core.service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.naukriradar.core.dto.response.ProfileResponse;
import com.naukriradar.core.dto.response.SkillResponse;
import com.naukriradar.core.model.SkillSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProfileFactsAnswererTest {

	private final ProfileFactsAnswerer answerer = new ProfileFactsAnswerer();

	private final ProfileResponse profile = new ProfileResponse("u", "Asha Rao", null, "Pune", "Backend Engineer", 4,
			1_800_000L, 0, "https://linkedin.com/in/asha", null, null, List.of(), List.of(), List.of(), List.of(), true, 50, 10,
			false, null);

	private final List<SkillResponse> skills = List.of(new SkillResponse("java", 3, SkillSource.MANUAL),
			new SkillResponse("kafka", null, SkillSource.RESUME));

	@Test
	void profileFieldsAnswerTheirQuestionsExactly() {
		assertThat(ask("What is your notice period?")).contains("I can join immediately.");
		assertThat(ask("How many years of experience do you have?")).contains("4 years.");
		assertThat(ask("Years of Java experience")).contains("3 years.");
		assertThat(ask("What is your current location?")).contains("Pune.");
		assertThat(ask("Expected CTC?")).hasValueSatisfying(a -> assertThat(a).startsWith("INR 18,00,000"));
		assertThat(ask("Current designation")).contains("Backend Engineer.");
		assertThat(ask("LinkedIn profile URL")).contains("https://linkedin.com/in/asha");
	}

	@Test
	void whatTheProfileCantAnswerForSureIsLeftAlone() {
		// years in a skill we don't know the years of: the total would be misleading
		assertThat(ask("How many years of Kafka experience?")).isEmpty();
		assertThat(ask("Why do you want to join us?")).isEmpty();
		assertThat(ask("GitHub link")).isEmpty();
		// "javascript" must not be read as "java"
		assertThat(ask("How many years of JavaScript experience?")).contains("4 years.");
	}

	private Optional<String> ask(String question) {
		return answerer.answer(question, profile, skills);
	}

}
