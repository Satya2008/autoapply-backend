package com.naukriradar.core.controller;

import com.naukriradar.common.security.CurrentUserProvider;
import com.naukriradar.core.dto.request.NotificationPreferencesRequest;
import com.naukriradar.core.dto.response.NotificationPreferencesResponse;
import com.naukriradar.core.dto.response.TelegramLinkResponse;
import com.naukriradar.core.service.NotificationPreferenceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
public class NotificationPreferenceController {

	private final CurrentUserProvider currentUser;
	private final NotificationPreferenceService preferences;

	public NotificationPreferenceController(CurrentUserProvider currentUser, NotificationPreferenceService preferences) {
		this.currentUser = currentUser;
		this.preferences = preferences;
	}

	@GetMapping("/notification-preferences")
	public NotificationPreferencesResponse get() {
		return preferences.get(currentUser.currentUserId());
	}

	@PutMapping("/notification-preferences")
	public NotificationPreferencesResponse update(@Valid @RequestBody NotificationPreferencesRequest request) {
		return preferences.update(currentUser.currentUserId(), request);
	}

	/** A one-time code to send to the bot; links this account to the Telegram chat it comes from. */
	@PostMapping("/telegram/link")
	public TelegramLinkResponse linkTelegram() {
		return preferences.newLinkCode(currentUser.currentUserId());
	}

}
