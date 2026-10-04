package com.naukriradar.core.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** How a user wants to hear from us. A user without a row gets the defaults. */
@Entity
@Table(name = "notification_preferences")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationPreference {

	@Id
	@Column(name = "user_id", length = 36)
	private String userId;

	@Column(nullable = false)
	private boolean emailEnabled = true;

	@Column(nullable = false)
	private boolean telegramEnabled;

	@Column(length = 40)
	private String telegramChatId;

	@Column(nullable = false)
	private boolean digestEnabled = true;

	/** A message when an apply run sent something or left something for the user. */
	@Column(nullable = false)
	private boolean applyUpdates = true;

	/** One-time code the user sends to the bot to link their chat; null when none is pending. */
	@Column(length = 12)
	private String telegramLinkCode;

	private Instant telegramLinkExpiresAt;

	@Column(nullable = false)
	private Instant updatedAt;

	public NotificationPreference(String userId, Instant now) {
		this.userId = userId;
		this.updatedAt = now;
	}

	public void change(boolean email, boolean telegram, boolean digest, boolean applyUpdates, Instant now) {
		this.emailEnabled = email;
		this.telegramEnabled = telegram && telegramChatId != null;
		this.digestEnabled = digest;
		this.applyUpdates = applyUpdates;
		this.updatedAt = now;
	}

	public void newLinkCode(String code, Instant expiresAt, Instant now) {
		this.telegramLinkCode = code;
		this.telegramLinkExpiresAt = expiresAt;
		this.updatedAt = now;
	}

	public void linkTelegram(String chatId, Instant now) {
		this.telegramChatId = chatId;
		this.telegramEnabled = true;
		this.telegramLinkCode = null;
		this.telegramLinkExpiresAt = null;
		this.updatedAt = now;
	}

}
