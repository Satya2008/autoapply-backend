package com.naukriradar.config;

import java.util.UUID;

import com.naukriradar.common.CurrentUserProvider;
import com.naukriradar.common.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Development stand-in for authentication: trusts the {@code X-User-Id} header. Active
 * only in dev and test, so any other profile fails to start until a real provider exists.
 */
@Component
@Profile({ "dev", "test" })
@RequiredArgsConstructor
class HeaderCurrentUserProvider implements CurrentUserProvider {

	static final String HEADER = "X-User-Id";

	/** A proxy that resolves to the request on the current thread. */
	private final HttpServletRequest request;

	@Override
	public UUID currentUserId() {
		String value = request.getHeader(HEADER);
		if (value == null || value.isBlank()) {
			throw new UnauthenticatedException("Send the " + HEADER + " header.");
		}
		try {
			return UUID.fromString(value.trim());
		}
		catch (IllegalArgumentException ex) {
			throw new UnauthenticatedException(HEADER + " must be a UUID.");
		}
	}

}
