package com.naukriradar.core.settings;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.FieldSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SettingValuesTest {

	static final List<SettingDefinition> ALL = SettingDefinitions.all();

	@ParameterizedTest(name = "{0}")
	@FieldSource("ALL")
	void everyDefaultPassesItsOwnValidation(SettingDefinition definition) {
		assertThatCode(() -> SettingValues.validate(definition, definition.defaultValue())).doesNotThrowAnyException();
	}

	@Test
	void intsAreCheckedAgainstTheirRange() {
		SettingDefinition attempts = SettingDefinitions.get(SettingDefinitions.MAX_ATTEMPTS);

		assertThat(SettingValues.validate(attempts, " 5 ")).isEqualTo("5");
		assertThatThrownBy(() -> SettingValues.validate(attempts, "0")).hasMessageContaining("at least 1");
		assertThatThrownBy(() -> SettingValues.validate(attempts, "11")).hasMessageContaining("at most 10");
		assertThatThrownBy(() -> SettingValues.validate(attempts, "three")).hasMessageContaining("whole number");
		assertThatThrownBy(() -> SettingValues.validate(attempts, "2.5")).hasMessageContaining("whole number");
	}

	@ParameterizedTest
	@CsvSource({ "TRUE, true", "false, false", "  True , true" })
	void booleansAreNormalised(String raw, String expected) {
		assertThat(SettingValues.validate(SettingDefinitions.get(SettingDefinitions.AUTO_APPLY_ENABLED), raw)).isEqualTo(expected);
	}

	@Test
	void booleansMustBeTrueOrFalse() {
		assertThatThrownBy(() -> SettingValues.validate(SettingDefinitions.get(SettingDefinitions.AUTO_APPLY_ENABLED), "yes"))
				.hasMessageContaining("true or false");
	}

	@Test
	void durationsAreReadAndBounded() {
		SettingDefinition delay = SettingDefinitions.get(SettingDefinitions.RETRY_DELAY);

		assertThat(SettingValues.asDuration(SettingValues.validate(delay, "45m"))).isEqualTo(Duration.ofMinutes(45));
		assertThat(SettingValues.asDuration(SettingValues.validate(delay, "2h"))).isEqualTo(Duration.ofHours(2));
		assertThatThrownBy(() -> SettingValues.validate(delay, "soon")).hasMessageContaining("duration");
		assertThatThrownBy(() -> SettingValues.validate(delay, "0s")).hasMessageContaining("more than zero");
		assertThatThrownBy(() -> SettingValues.validate(delay, "2d")).hasMessageContaining("at most 1440 minutes");
	}

	@Test
	void cronNeedsSixValidFields() {
		SettingDefinition cron = SettingDefinitions.get(SettingDefinitions.AUTO_APPLY_CRON);

		assertThat(SettingValues.validate(cron, "0 30 9 * * MON-FRI")).isEqualTo("0 30 9 * * MON-FRI");
		assertThatThrownBy(() -> SettingValues.validate(cron, "30 9 * * *")).hasMessageContaining("six fields");
		assertThatThrownBy(() -> SettingValues.validate(cron, "0 99 9 * * *")).hasMessageContaining("cron");
	}

	@Test
	void domainListsAreCleanedAndChecked() {
		SettingDefinition high = SettingDefinitions.get(SettingDefinitions.HIGH_RISK_DOMAINS);

		assertThat(SettingValues.validate(high, " LinkedIn.com,www.naukri.com  linkedin.com\nindeed.com "))
				.isEqualTo("linkedin.com, naukri.com, indeed.com");
		assertThatThrownBy(() -> SettingValues.validate(high, "linkedin.com, not a domain"))
				.hasMessageContaining("'not' is not a domain");
		assertThatThrownBy(() -> SettingValues.validate(high, "https://linkedin.com"))
				.hasMessageContaining("is not a domain");
	}

	@Test
	void valuesHaveALengthLimit() {
		assertThatThrownBy(() -> SettingValues.validate(SettingDefinitions.get(SettingDefinitions.AI_API_KEY), "x".repeat(4001)))
				.hasMessageContaining("longer than");
	}

}
