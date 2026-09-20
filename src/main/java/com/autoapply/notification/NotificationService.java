package com.autoapply.notification;

import com.autoapply.audit.AuditAction;
import com.autoapply.audit.AuditService;
import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** Fans an event out to whichever channels the dashboard currently has switched on. */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final List<NotificationChannel> channels;
    private final SettingsService settings;
    private final AuditService auditService;

    @Async
    public void dispatch(NotificationEvent event) {
        if (!settings.getBoolean(SettingKeys.NOTIFY_ENABLED, false)) return;

        List<String> active = settings.getList(SettingKeys.NOTIFY_CHANNELS);
        if (active.isEmpty()) return;

        for (NotificationChannel channel : channels) {
            if (!active.contains(channel.name())) continue;
            if (!channel.isConfigured()) {
                log.debug("Channel {} is enabled but not configured - skipping", channel.name());
                continue;
            }
            try {
                channel.send(event);
                auditService.record("system", AuditAction.NOTIFICATION_SENT, "notification",
                        event.getType(), channel.name() + " -> " + event.getRecipientEmail());
            } catch (Exception e) {
                log.warn("Channel {} failed to send {}: {}", channel.name(), event.getType(), e.getMessage());
                auditService.recordFailure("system", AuditAction.NOTIFICATION_FAILED, "notification",
                        event.getType(), channel.name() + ": " + e.getMessage());
            }
        }
    }

    /** Sends a test message through one channel so an admin can verify credentials. */
    public String test(String channelName, String recipient) {
        NotificationChannel channel = channels.stream()
                .filter(c -> c.name().equalsIgnoreCase(channelName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown channel: " + channelName));

        if (!channel.isConfigured()) {
            return channel.name() + " is not configured yet - fill in its settings first";
        }
        try {
            NotificationEvent event = NotificationEvent.builder()
                    .type("TEST")
                    .recipientEmail(recipient)
                    .recipientName("there")
                    .subject("Test notification from " + settings.getString(SettingKeys.APP_NAME, "AutoApply AI"))
                    .body("If you are reading this, the " + channel.name() + " channel works.")
                    .build();
            channel.send(event);
            return "Test message sent through " + channel.name();
        } catch (Exception e) {
            return "Failed: " + e.getMessage();
        }
    }

    public List<Map<String, Object>> channelStatus() {
        List<String> active = settings.getList(SettingKeys.NOTIFY_CHANNELS);
        return channels.stream()
                .map(c -> Map.<String, Object>of(
                        "name", c.name(),
                        "configured", c.isConfigured(),
                        "enabled", active.contains(c.name())))
                .toList();
    }
}
