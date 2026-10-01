package com.naukriradar.user;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
		UUID id,
		String email,
		UserStatus status,
		Instant createdAt) {
}
