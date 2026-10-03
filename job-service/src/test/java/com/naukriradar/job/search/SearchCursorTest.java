package com.naukriradar.job.search;

import java.time.Instant;
import java.util.UUID;

import com.naukriradar.common.exception.BadRequestException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SearchCursorTest {

	@Test
	void roundTripsToTheMicrosecond() {
		SearchCursor cursor = new SearchCursor(Instant.parse("2026-10-03T10:15:30.123456Z"), UUID.randomUUID().toString());

		assertThat(SearchCursor.decode(cursor.encode())).isEqualTo(cursor);
	}

	@Test
	void encodedFormIsUrlSafe() {
		SearchCursor cursor = new SearchCursor(Instant.parse("2026-10-03T10:15:30Z"), UUID.randomUUID().toString());

		assertThat(cursor.encode()).matches("[A-Za-z0-9_-]+");
	}

	@ParameterizedTest
	@ValueSource(strings = { "not base64!", "bm8tYmFy", "MTIzfG5vdC1hLXV1aWQ", "YWJjfDEyMw" })
	void tamperedCursorsAreABadRequest(String cursor) {
		assertThatThrownBy(() -> SearchCursor.decode(cursor)).isInstanceOf(BadRequestException.class);
	}

}
