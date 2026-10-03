package com.naukriradar.job.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Time-ordered UUIDs (RFC 9562, version 7) for rows written with plain JDBC, matching what
 * Hibernate generates for entities. Ids made later sort later, which keeps InnoDB inserts
 * at the end of the primary key index.
 */
public final class UuidV7 {

	private static final SecureRandom RANDOM = new SecureRandom();

	private UuidV7() {
	}

	public static String generate() {
		return generate(Instant.now());
	}

	static String generate(Instant at) {
		long millis = at.toEpochMilli();
		long randA = RANDOM.nextInt(1 << 12);
		long mostSig = (millis << 16) | (0x7L << 12) | randA;
		long randB = RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL;
		long leastSig = 0x8000_0000_0000_0000L | randB;
		return new UUID(mostSig, leastSig).toString();
	}

}
