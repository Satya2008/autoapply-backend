package com.naukriradar.core.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.naukriradar.common.events.OutboxWriter;
import com.naukriradar.common.events.Topics;
import com.naukriradar.core.model.NotificationPreference;
import com.naukriradar.core.model.Profile;
import com.naukriradar.core.repository.ProfileRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asks notification-service to tell a user something. core-api knows the user, so it decides
 * here who gets it and on which channels (their preferences, their addresses); the
 * notification service only renders and delivers. Goes through the outbox, so a message is
 * requested exactly when the change it is about commits.
 */
@Component
public class NotificationRequests {

	public enum Kind {
		DIGEST, APPLY_UPDATE
	}

	private final NotificationPreferenceService preferences;
	private final ProfileRepository profiles;
	private final OutboxWriter outbox;

	public NotificationRequests(NotificationPreferenceService preferences, ProfileRepository profiles, OutboxWriter outbox) {
		this.preferences = preferences;
		this.profiles = profiles;
		this.outbox = outbox;
	}

	/** @return false when the user doesn't want this kind of message, or can't be reached */
	@Transactional(propagation = Propagation.MANDATORY)
	public boolean request(String userId, Kind kind, String template, Map<String, Object> data) {
		NotificationPreference preference = preferences.forUser(userId);
		if (kind == Kind.DIGEST && !preference.isDigestEnabled() || kind == Kind.APPLY_UPDATE && !preference.isApplyUpdates()) {
			return false;
		}
		Profile profile = profiles.findById(userId).orElse(null);
		String email = profile == null ? null : profile.getUser().getEmail();
		List<String> channels = new ArrayList<>();
		if (preference.isEmailEnabled() && email != null) {
			channels.add("EMAIL");
		}
		if (preference.isTelegramEnabled() && preference.getTelegramChatId() != null) {
			channels.add("TELEGRAM");
		}
		if (channels.isEmpty()) {
			return false;
		}
		String name = profile == null || profile.getFullName() == null ? null : profile.getFullName();
		outbox.publish(Topics.NOTIFY_REQUESTED, userId, "NotifyRequested", Map.of(
				"template", template,
				"recipient", new Recipient(userId, name, email, preference.getTelegramChatId()),
				"channels", channels,
				"data", data));
		return true;
	}

	record Recipient(String userId, String name, String email, String telegramChatId) {
	}

}
