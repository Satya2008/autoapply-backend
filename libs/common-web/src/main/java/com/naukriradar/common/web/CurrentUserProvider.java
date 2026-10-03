package com.naukriradar.common.web;

import java.util.UUID;

/**
 * Identifies the caller of the current request. Controllers depend on this, never on how
 * the identity arrives.
 */
public interface CurrentUserProvider {

	/**
	 * Header carrying the caller's user id. The gateway is the only public entry point and
	 * is responsible for setting it; services trust it.
	 */
	String USER_ID_HEADER = "X-User-Id";

	/**
	 * @throws UnauthenticatedException if the caller cannot be identified
	 */
	UUID currentUserId();

}
