package com.autoapply.matching.ai;

import com.autoapply.common.AppException;
import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class AnthropicProvider implements AiProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final SettingsService settings;

    @Override
    public String name() {
        return "anthropic";
    }

    @Override
    public boolean isConfigured() {
        return settings.isConfigured(SettingKeys.ANTHROPIC_API_KEY);
    }

    @Override
    public String complete(String prompt) {
        String key = settings.getString(SettingKeys.ANTHROPIC_API_KEY);
        String baseUrl = settings.getString(SettingKeys.ANTHROPIC_BASE_URL, "https://api.anthropic.com/v1");
        String model = settings.getString(SettingKeys.ANTHROPIC_MODEL, "claude-sonnet-5");
        String version = settings.getString(SettingKeys.ANTHROPIC_VERSION, "2023-06-01");

        if (key == null || key.isBlank()) {
            throw AppException.badRequest("Anthropic API key is not configured");
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "max_tokens", settings.getInt(SettingKeys.AI_MAX_TOKENS, 1024),
                "temperature", settings.getDouble(SettingKeys.AI_TEMPERATURE, 0.2),
                "messages", List.of(Map.of("role", "user", "content", prompt)));

        String response = webClient.post()
                .uri(baseUrl + "/messages")
                .header("x-api-key", key)
                .header("anthropic-version", version)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(settings.getInt(SettingKeys.AI_TIMEOUT_SECONDS, 30)))
                .block();

        try {
            return objectMapper.readTree(response).path("content").get(0).path("text").asText();
        } catch (Exception e) {
            throw AppException.internal("Unexpected Anthropic response: " + e.getMessage());
        }
    }
}
