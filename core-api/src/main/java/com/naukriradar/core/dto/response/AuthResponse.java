package com.naukriradar.core.dto.response;

import com.naukriradar.core.model.UserRole;

/**
 * A signed-in session.
 *
 * @param token sent back as "Authorization: Bearer ..."; shown once, only its hash is kept
 * @param mustChangePassword the password was set by someone else: change it before anything else
 */
public record AuthResponse(String token, String userId, String email, String name, UserRole role,
		boolean mustChangePassword) {
}
