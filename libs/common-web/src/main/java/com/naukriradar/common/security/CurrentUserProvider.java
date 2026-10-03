package com.naukriradar.common.security;

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
	 * Returns the caller's user id in canonical form (lower-case UUID).
	 * @throws com.naukriradar.common.exception.UnauthenticatedException if the caller
	 * cannot be identified
	 */
	String currentUserId();

}
