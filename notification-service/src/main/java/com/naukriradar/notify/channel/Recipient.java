package com.naukriradar.notify.channel;

/** Where to reach one user; a channel without its address is skipped. */
public record Recipient(String userId, String name, String email, String telegramChatId) {
}
