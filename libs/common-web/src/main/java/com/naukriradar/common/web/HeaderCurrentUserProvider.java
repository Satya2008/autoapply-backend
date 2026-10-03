package com.naukriradar.common.web;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Reads the caller from the {@value CurrentUserProvider#USER_ID_HEADER} header. Services
 * sit behind the gateway, which sets this header; until the gateway verifies JWTs
 * (Phase 8), clients send it themselves in dev.
 */
@RequiredArgsConstructor
class HeaderCurrentUserProvider implements CurrentUserProvider {

	/** A proxy that resolves to the request on the current thread. */
	private final HttpServletRequest request;

	@Override
	public UUID currentUserId() {
		String value = request.getHeader(USER_ID_HEADER);
		if (value == null || value.isBlank()) {
			throw new UnauthenticatedException("Send the " + USER_ID_HEADER + " header.");
		}
		try {
			return UUID.fromString(value.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new UnauthenticatedException(USER_ID_HEADER + " must be a UUID.");
		}
	}

}
