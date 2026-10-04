package com.naukriradar.notify.channel;

/** One rendered notification: a subject, an HTML body for email and plain text for chat. */
public record Message(String subject, String html, String text) {
}
