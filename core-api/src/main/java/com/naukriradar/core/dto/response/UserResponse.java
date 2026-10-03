package com.naukriradar.core.dto.response;

import java.time.Instant;

import com.naukriradar.core.model.UserStatus;

public record UserResponse(
		String id,
		String email,
		UserStatus status,
		Instant createdAt) {
}
