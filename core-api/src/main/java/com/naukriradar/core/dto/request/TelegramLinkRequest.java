package com.naukriradar.core.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** From notification-service, after a user sent the bot their code. */
public record TelegramLinkRequest(@NotBlank @Size(max = 12) String code, @NotBlank @Size(max = 40) String chatId) {
}
