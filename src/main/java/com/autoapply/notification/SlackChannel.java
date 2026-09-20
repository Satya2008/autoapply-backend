package com.autoapply.notification;

import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class SlackChannel implements NotificationChannel {

    private final WebClient webClient;
    private final SettingsService settings;

    @Override
    public String name() {
        return "slack";
    }

    @Override
    public boolean isConfigured() {
        return settings.isConfigured(SettingKeys.SLACK_WEBHOOK_URL);
    }

    @Override
    public void send(NotificationEvent event) {
        String webhook = settings.getString(SettingKeys.SLACK_WEBHOOK_URL);
        if (webhook == null || webhook.isBlank()) return;

        String text = "*" + event.getSubject() + "*\n" + event.getBody()
                + (event.getRecipientEmail() == null ? "" : "\n_for " + event.getRecipientEmail() + "_");

        webClient.post()
                .uri(webhook)
                .header("Content-Type", "application/json")
                .bodyValue(Map.of("text", text))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .block();
    }
}
