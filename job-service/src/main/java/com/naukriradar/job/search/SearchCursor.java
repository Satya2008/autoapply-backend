package com.naukriradar.job.search;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import com.naukriradar.common.exception.BadRequestException;

/**
 * Where a page ended: the last row's (sortAt, id), plus an optional {@code rank} that carries the
 * sort key when ordering by something other than time (salary). The next page asks for rows "after"
 * this point, which an index can jump to directly, unlike OFFSET which reads and throws away every
 * earlier row. The value is opaque to clients (base64), so the format can change later.
 */
public record SearchCursor(Instant sortAt, String id, Long rank) {

	public SearchCursor(Instant sortAt, String id) {
		this(sortAt, id, null);
	}

	public String encode() {
		long micros = ChronoUnit.MICROS.between(Instant.EPOCH, sortAt);
		String raw = rank == null ? micros + "|" + id : micros + "|" + id + "|" + rank;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	public static SearchCursor decode(String cursor) {
		try {
			String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			String[] parts = raw.split("\\|", 3);
			long micros = Long.parseLong(parts[0]);
			String id = parts[1];
			UUID.fromString(id);
			Long rank = parts.length > 2 ? Long.parseLong(parts[2]) : null;
			return new SearchCursor(Instant.EPOCH.plus(micros, ChronoUnit.MICROS), id, rank);
		}
		catch (IllegalArgumentException | IndexOutOfBoundsException | ArithmeticException ex) {
			throw new BadRequestException("Invalid cursor. Use the nextCursor value from the previous page.");
		}
	}

}
