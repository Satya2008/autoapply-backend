package com.naukriradar.core.dto.request;

import jakarta.validation.constraints.NotNull;

/** Telegram can only be switched on once a chat is linked (POST /api/v1/me/telegram/link). */
public record NotificationPreferencesRequest(
		@NotNull Boolean emailEnabled,
		@NotNull Boolean telegramEnabled,
		@NotNull Boolean digestEnabled,
		@NotNull Boolean applyUpdates) {
}
