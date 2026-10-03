package com.naukriradar.common.security;

import java.util.UUID;

import com.naukriradar.common.exception.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

/**
 * Reads the caller from the {@value CurrentUserProvider#USER_ID_HEADER} header that the
 * gateway sets. Until the gateway verifies JWTs, clients send it themselves in dev.
 */
@RequiredArgsConstructor
public class HeaderCurrentUserProvider implements CurrentUserProvider {

	/** A proxy that resolves to the request on the current thread. */
	private final HttpServletRequest request;

	@Override
	public String currentUserId() {
		String value = request.getHeader(USER_ID_HEADER);
		if (value == null || value.isBlank()) {
			throw new UnauthenticatedException("Send the " + USER_ID_HEADER + " header.");
		}
		try {
			// Round-trip through UUID so "ABC..." and "abc..." resolve to the same user.
			return UUID.fromString(value.trim()).toString();
		}
		catch (IllegalArgumentException ex) {
			throw new UnauthenticatedException(USER_ID_HEADER + " must be a UUID.");
		}
	}

}
