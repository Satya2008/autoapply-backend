package com.autoapply.notification;

import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/** Posts the event as JSON anywhere - Zapier, n8n, Make, or an in-house service. */
@Component
@RequiredArgsConstructor
public class WebhookChannel implements NotificationChannel {

    private final WebClient webClient;
    private final SettingsService settings;

    @Override
    public String name() {
        return "webhook";
    }

    @Override
    public boolean isConfigured() {
        return settings.isConfigured(SettingKeys.GENERIC_WEBHOOK_URL);
    }

    @Override
    public void send(NotificationEvent event) {
        String url = settings.getString(SettingKeys.GENERIC_WEBHOOK_URL);
        if (url.isBlank()) return;

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", event.getType());
        payload.put("subject", event.getSubject());
        payload.put("body", event.getBody());
        payload.put("recipient", event.getRecipientEmail());
        payload.put("data", event.getData());
        payload.put("sentAt", Instant.now().toString());

        WebClient.RequestBodySpec request = webClient.post().uri(url);
        settings.getMap(SettingKeys.GENERIC_WEBHOOK_HEADERS).forEach(request::header);

        request.header("Content-Type", "application/json")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .block();
    }
}
