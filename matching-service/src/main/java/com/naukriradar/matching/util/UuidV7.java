package com.naukriradar.matching.util;

import java.time.Instant;
import java.util.UUID;

/** Time-ordered UUIDs (version 7) for rows written with plain JDBC, like Hibernate makes for entities. */
public final class UuidV7 {

	private UuidV7() {
	}

	public static String at(Instant at) {
		long random = UUID.randomUUID().getLeastSignificantBits();
		long mostSig = (at.toEpochMilli() << 16) | (0x7L << 12) | (random & 0xFFF);
		long leastSig = 0x8000_0000_0000_0000L | (UUID.randomUUID().getMostSignificantBits() & 0x3FFF_FFFF_FFFF_FFFFL);
		return new UUID(mostSig, leastSig).toString();
	}

}
