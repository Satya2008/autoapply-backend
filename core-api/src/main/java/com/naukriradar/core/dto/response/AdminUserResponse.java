package com.naukriradar.core.dto.response;

import java.time.Instant;

import com.naukriradar.core.model.UserRole;
import com.naukriradar.core.model.UserStatus;

/**
 * A user as the admin sees them in the list: who, and how far they got.
 *
 * @param needsYou applications waiting for the user
 * @param applied applications that reached an employer
 */
public record AdminUserResponse(String id, String email, String name, UserRole role, UserStatus status, Instant createdAt,
		Instant lastLoginAt, boolean hasPassword, boolean hasResume, int skills, long applications, long needsYou,
		long applied) {
}
