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
import java.util.Map;

/** Local, zero-cost inference through an Ollama server. */
@Component
@RequiredArgsConstructor
@Slf4j
public class OllamaProvider implements AiProvider {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final SettingsService settings;

    @Override
    public String name() {
        return "ollama";
    }

    @Override
    public boolean isConfigured() {
        return settings.isConfigured(SettingKeys.OLLAMA_BASE_URL);
    }

    @Override
    public String complete(String prompt) {
        String baseUrl = settings.getString(SettingKeys.OLLAMA_BASE_URL, "http://localhost:11434");
        String model = settings.getString(SettingKeys.OLLAMA_MODEL, "llama3");

        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false,
                "options", Map.of("temperature", settings.getDouble(SettingKeys.AI_TEMPERATURE, 0.2)));

        String response = webClient.post()
                .uri(baseUrl + "/api/generate")
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(settings.getInt(SettingKeys.AI_TIMEOUT_SECONDS, 30)))
                .block();

        try {
            return objectMapper.readTree(response).path("response").asText();
        } catch (Exception e) {
            throw AppException.internal("Unexpected Ollama response: " + e.getMessage());
        }
    }
}
