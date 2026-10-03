package com.naukriradar.core.service;

import com.naukriradar.common.exception.BadRequestException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortalDomainTest {

	@ParameterizedTest(name = "{0} -> {1}")
	@CsvSource({
			"careers.acme.com, careers.acme.com",
			"https://www.Careers.Acme.com/jobs?x=1, careers.acme.com",
			"HTTP://jobs.lever.co:443/acme, jobs.lever.co",
			"acme.com., acme.com" })
	void normalisesToABareHost(String input, String expected) {
		assertThat(PortalConfigService.normaliseDomain(input)).isEqualTo(expected);
	}

	@ParameterizedTest
	@ValueSource(strings = { "localhost", "acme", "http://", "-bad-.com", "acme..com", "a b.com", "10.0.0.1" })
	void rejectsThingsThatAreNotDomains(String input) {
		assertThatThrownBy(() -> PortalConfigService.normaliseDomain(input)).isInstanceOf(BadRequestException.class);
	}

}
