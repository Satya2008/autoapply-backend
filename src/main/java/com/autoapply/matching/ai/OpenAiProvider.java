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

/** Works with OpenAI and any OpenAI-compatible endpoint (Groq, Together, LM Studio, vLLM). */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpenAiProvider implements AiProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final SettingsService settings;

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public boolean isConfigured() {
        return settings.isConfigured(SettingKeys.OPENAI_API_KEY);
    }

    @Override
    public String complete(String prompt) {
        String key = settings.getString(SettingKeys.OPENAI_API_KEY);
        String baseUrl = settings.getString(SettingKeys.OPENAI_BASE_URL, "https://api.openai.com/v1");
        String model = settings.getString(SettingKeys.OPENAI_MODEL, "gpt-4o-mini");

        if (key == null || key.isBlank()) {
            throw AppException.badRequest("OpenAI API key is not configured");
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "temperature", settings.getDouble(SettingKeys.AI_TEMPERATURE, 0.2),
                "max_tokens", settings.getInt(SettingKeys.AI_MAX_TOKENS, 1024));

        String response = webClient.post()
                .uri(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + key)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(settings.getInt(SettingKeys.AI_TIMEOUT_SECONDS, 30)))
                .block();

        try {
            return objectMapper.readTree(response)
                    .path("choices").get(0).path("message").path("content").asText();
        } catch (Exception e) {
            throw AppException.internal("Unexpected OpenAI response: " + e.getMessage());
        }
    }
}
