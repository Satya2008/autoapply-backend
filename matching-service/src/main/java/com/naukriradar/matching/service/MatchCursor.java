package com.naukriradar.matching.service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import com.naukriradar.common.exception.BadRequestException;

/** Where a page of matches ended: the last (score, id). Opaque to clients. */
record MatchCursor(int score, String id) {

	String encode() {
		return Base64.getUrlEncoder().withoutPadding()
				.encodeToString((score + "|" + id).getBytes(StandardCharsets.UTF_8));
	}

	static MatchCursor decode(String cursor) {
		try {
			String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			int bar = raw.indexOf('|');
			int score = Integer.parseInt(raw.substring(0, bar));
			String id = raw.substring(bar + 1);
			UUID.fromString(id);
			if (score < 0 || score > 100) {
				throw new IllegalArgumentException("score out of range");
			}
			return new MatchCursor(score, id);
		}
		catch (IllegalArgumentException | IndexOutOfBoundsException ex) {
			throw new BadRequestException("Invalid cursor. Use the nextCursor value from the previous page.");
		}
	}

}
