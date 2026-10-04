package com.naukriradar.core.dto.response;

import java.time.Instant;

/** @param instructions what the user does with the code */
public record TelegramLinkResponse(String code, Instant expiresAt, String instructions) {
}
