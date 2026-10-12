package com.naukriradar.core.dto.response;

import com.naukriradar.core.model.UserRole;

/** For the gateway: whose session a token is. */
public record SessionUserResponse(String userId, UserRole role) {
}
