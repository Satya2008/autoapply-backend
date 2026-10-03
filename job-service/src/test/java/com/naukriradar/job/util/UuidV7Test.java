package com.naukriradar.job.util;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UuidV7Test {

	@Test
	void isAVersion7RfcVariantUuid() {
		UUID uuid = UUID.fromString(UuidV7.generate());

		assertThat(uuid.version()).isEqualTo(7);
		assertThat(uuid.variant()).isEqualTo(2);
	}

	@Test
	void laterIdsSortLater() {
		String earlier = UuidV7.generate(Instant.parse("2026-10-03T10:00:00Z"));
		String later = UuidV7.generate(Instant.parse("2026-10-03T10:00:00.001Z"));

		assertThat(later).isGreaterThan(earlier);
	}

	@Test
	void carriesTheTimestamp() {
		Instant at = Instant.parse("2026-10-03T10:15:30.123Z");
		UUID uuid = UUID.fromString(UuidV7.generate(at));

		assertThat(uuid.getMostSignificantBits() >>> 16).isEqualTo(at.toEpochMilli());
	}

}
