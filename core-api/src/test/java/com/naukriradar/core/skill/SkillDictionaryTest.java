package com.naukriradar.core.skill;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

class SkillDictionaryTest {

	@Test
	void readsCanonicalNamesAliasesAndSkipsComments() {
		SkillDictionary dictionary = load("""
				# languages
				Java
				JavaScript | JS ,  ECMAScript   # inline comment

				""");

		assertThat(dictionary.terms()).containsExactly(
				entry("java", "java"),
				entry("javascript", "javascript"),
				entry("js", "javascript"),
				entry("ecmascript", "javascript"));
	}

	@Test
	void hashInsideASkillIsNotAComment() {
		SkillDictionary dictionary = load("c# | csharp   # Microsoft's language");

		assertThat(dictionary.terms()).containsOnlyKeys("c#", "csharp");
	}

	@Test
	void refusesOneSpellingForTwoSkills() {
		assertThatThrownBy(() -> load("""
				react native
				react | reactnative
				"""))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("reactnative");
	}

	@Test
	void refusesNamesTooLongForTheProfile() {
		assertThatThrownBy(() -> load("x".repeat(SkillDictionary.MAX_SKILL_LENGTH + 1)))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void shippedDictionaryLoads() {
		assertThat(SkillDictionary.load(new ClassPathResource("skills/dictionary.txt")).terms()).isNotEmpty();
	}

	private static SkillDictionary load(String text) {
		return SkillDictionary.load(new ByteArrayResource(text.getBytes(StandardCharsets.UTF_8)));
	}

}
