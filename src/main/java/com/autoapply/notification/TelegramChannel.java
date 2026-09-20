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
public class TelegramChannel implements NotificationChannel {

    private final WebClient webClient;
    private final SettingsService settings;

    @Override
    public String name() {
        return "telegram";
    }

    @Override
    public boolean isConfigured() {
        return settings.isConfigured(SettingKeys.TELEGRAM_BOT_TOKEN)
                && settings.isConfigured(SettingKeys.TELEGRAM_CHAT_ID);
    }

    @Override
    public void send(NotificationEvent event) {
        String token = settings.getString(SettingKeys.TELEGRAM_BOT_TOKEN);
        String chatId = settings.getString(SettingKeys.TELEGRAM_CHAT_ID);
        if (token.isBlank() || chatId.isBlank()) return;

        webClient.post()
                .uri("https://api.telegram.org/bot" + token + "/sendMessage")
                .header("Content-Type", "application/json")
                .bodyValue(Map.of(
                        "chat_id", chatId,
                        "text", event.getSubject() + "\n\n" + event.getBody(),
                        "disable_web_page_preview", true))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(10))
                .block();
    }
}
