package com.naukriradar.job.search;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class LocationTermsTest {

	@ParameterizedTest
	@ValueSource(strings = { "Bengaluru, Karnataka", "Bangalore, IND", "Remote - India", "Noida, Uttar Pradesh", "Gurugram",
			"Hyderabad, Andhra Pradesh, India", "mumbai" })
	void indianLocationsAreInIndia(String location) {
		assertThat(LocationTerms.inIndia(location)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { "Indianapolis, IN", "Minden, Germany", "San Francisco, CA", "Remote - US", "London" })
	void othersAreNot(String location) {
		assertThat(LocationTerms.inIndia(location)).isFalse();
	}

}
