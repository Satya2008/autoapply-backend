package com.naukriradar.core.dto.response;

public record NotificationPreferencesResponse(boolean emailEnabled, boolean telegramEnabled, boolean telegramLinked,
		boolean digestEnabled, boolean applyUpdates) {
}
