package com.naukriradar.core.dto.response;

import com.naukriradar.core.model.UserRole;

/** Who is signed in. */
public record MeResponse(String userId, String email, String name, UserRole role, boolean mustChangePassword) {
}
