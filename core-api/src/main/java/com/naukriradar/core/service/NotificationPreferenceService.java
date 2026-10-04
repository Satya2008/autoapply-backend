package com.naukriradar.core.service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import com.naukriradar.common.exception.BusinessRuleException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.dto.request.NotificationPreferencesRequest;
import com.naukriradar.core.dto.response.NotificationPreferencesResponse;
import com.naukriradar.core.dto.response.TelegramLinkResponse;
import com.naukriradar.core.model.NotificationPreference;
import com.naukriradar.core.repository.NotificationPreferenceRepository;
import com.naukriradar.core.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceService {

	/** No 0/O or 1/I: the user may type it. */
	private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private static final int CODE_LENGTH = 8;

	private static final Duration CODE_VALID_FOR = Duration.ofMinutes(15);

	private final NotificationPreferenceRepository repository;
	private final UserRepository users;
	private final SecureRandom random = new SecureRandom();
	private final Clock clock = Clock.systemUTC();

	public NotificationPreferenceService(NotificationPreferenceRepository repository, UserRepository users) {
		this.repository = repository;
		this.users = users;
	}

	@Transactional(readOnly = true)
	public NotificationPreferencesResponse get(String userId) {
		requireUser(userId);
		return toResponse(repository.findById(userId).orElseGet(() -> new NotificationPreference(userId, clock.instant())));
	}

	@Transactional
	public NotificationPreferencesResponse update(String userId, NotificationPreferencesRequest request) {
		NotificationPreference preference = load(userId);
		if (request.telegramEnabled() && preference.getTelegramChatId() == null) {
			throw new BusinessRuleException("Link Telegram first: POST /api/v1/me/telegram/link.");
		}
		preference.change(request.emailEnabled(), request.telegramEnabled(), request.digestEnabled(),
				request.applyUpdates(), clock.instant());
		return toResponse(preference);
	}

	/** A fresh one-time code; the user sends "/start CODE" to the bot within 15 minutes. */
	@Transactional
	public TelegramLinkResponse newLinkCode(String userId) {
		NotificationPreference preference = load(userId);
		StringBuilder code = new StringBuilder(CODE_LENGTH);
		for (int i = 0; i < CODE_LENGTH; i++) {
			code.append(CODE_CHARS.charAt(random.nextInt(CODE_CHARS.length())));
		}
		Instant expires = clock.instant().plus(CODE_VALID_FOR);
		preference.newLinkCode(code.toString(), expires, clock.instant());
		return new TelegramLinkResponse(code.toString(), expires,
				"Open the NaukriRadar bot in Telegram and send: /start " + code);
	}

	/** Called for the bot. @throws NotFoundException for an unknown or expired code */
	@Transactional
	public void link(String code, String chatId) {
		NotificationPreference preference = repository.findByTelegramLinkCode(code.strip().toUpperCase())
				.filter(p -> p.getTelegramLinkExpiresAt() != null && p.getTelegramLinkExpiresAt().isAfter(clock.instant()))
				.orElseThrow(() -> new NotFoundException("Unknown or expired code."));
		preference.linkTelegram(chatId, clock.instant());
	}

	/** Preferences as stored, or the defaults; never null. */
	@Transactional(readOnly = true)
	public NotificationPreference forUser(String userId) {
		return repository.findById(userId).orElseGet(() -> new NotificationPreference(userId, clock.instant()));
	}

	private NotificationPreference load(String userId) {
		requireUser(userId);
		return repository.findById(userId)
				.orElseGet(() -> repository.save(new NotificationPreference(userId, clock.instant())));
	}

	private void requireUser(String userId) {
		if (!users.existsById(userId)) {
			throw new NotFoundException("No user " + userId + ".");
		}
	}

	private static NotificationPreferencesResponse toResponse(NotificationPreference p) {
		return new NotificationPreferencesResponse(p.isEmailEnabled(), p.isTelegramEnabled(), p.getTelegramChatId() != null,
				p.isDigestEnabled(), p.isApplyUpdates());
	}

}
