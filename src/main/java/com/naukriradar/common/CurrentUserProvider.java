package com.naukriradar.common;

import java.util.UUID;

/**
 * Identifies the caller of the current request. Controllers depend on this, never on how
 * the identity arrives, so swapping the dev header for JWT (Phase 8) changes no controller.
 */
public interface CurrentUserProvider {

	/**
	 * @throws UnauthenticatedException if the caller cannot be identified
	 */
	UUID currentUserId();

}
