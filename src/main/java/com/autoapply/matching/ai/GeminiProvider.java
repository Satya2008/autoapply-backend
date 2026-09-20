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
public class GeminiProvider implements AiProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final SettingsService settings;

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    public boolean isConfigured() {
        return settings.isConfigured(SettingKeys.GEMINI_API_KEY);
    }

    @Override
    public String complete(String prompt) {
        String key = settings.getString(SettingKeys.GEMINI_API_KEY);
        String baseUrl = settings.getString(SettingKeys.GEMINI_BASE_URL,
                "https://generativelanguage.googleapis.com/v1beta");
        String model = settings.getString(SettingKeys.GEMINI_MODEL, "gemini-1.5-flash");

        if (key == null || key.isBlank()) {
            throw AppException.badRequest("Gemini API key is not configured");
        }

        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of(
                        "temperature", settings.getDouble(SettingKeys.AI_TEMPERATURE, 0.2),
                        "maxOutputTokens", settings.getInt(SettingKeys.AI_MAX_TOKENS, 1024)));

        String response = webClient.post()
                .uri(baseUrl + "/models/" + model + ":generateContent?key=" + key)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(settings.getInt(SettingKeys.AI_TIMEOUT_SECONDS, 30)))
                .block();

        try {
            return objectMapper.readTree(response)
                    .path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();
        } catch (Exception e) {
            throw AppException.internal("Unexpected Gemini response: " + e.getMessage());
        }
    }
}
