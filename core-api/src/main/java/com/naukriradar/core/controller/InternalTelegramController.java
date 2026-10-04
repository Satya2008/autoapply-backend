package com.naukriradar.core.controller;

import com.naukriradar.core.dto.request.TelegramLinkRequest;
import com.naukriradar.core.service.NotificationPreferenceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** For notification-service's bot: a user sent their code, so link their chat. Not routed by the gateway. */
@RestController
@RequestMapping("/internal/v1/telegram")
public class InternalTelegramController {

	private final NotificationPreferenceService preferences;

	public InternalTelegramController(NotificationPreferenceService preferences) {
		this.preferences = preferences;
	}

	@PostMapping("/link")
	public ResponseEntity<Void> link(@Valid @RequestBody TelegramLinkRequest request) {
		preferences.link(request.code(), request.chatId());
		return ResponseEntity.noContent().build();
	}

}
