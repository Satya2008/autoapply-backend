package com.naukriradar.matching.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.naukriradar.matching.config.AiProperties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Google Gemini generateContent API. */
@Component
public class GeminiClient extends HttpAiClient {

	public GeminiClient(RestClient.Builder builder, JsonMapper json) {
		super(builder, json);
	}

	@Override
	public AiProperties.ProviderType type() {
		return AiProperties.ProviderType.GEMINI;
	}

	@Override
	public AiCompletion complete(AiProperties.Provider provider, String model, AiRequest request) {
		Map<String, Object> config = new HashMap<>();
		config.put("maxOutputTokens", request.maxTokens());
		if (request.outputSchema() != null) {
			config.put("responseMimeType", "application/json");
		}
		Map<String, Object> body = Map.of(
				"systemInstruction", Map.of("parts", List.of(Map.of("text", system(request)))),
				"contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", request.prompt())))),
				"generationConfig", config);
		JsonNode reply = client(provider).post()
				.uri("/v1beta/models/{model}:generateContent", model)
				.header("x-goog-api-key", provider.apiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(JsonNode.class);
		if (reply == null) {
			throw new AiProviderException("Empty reply.");
		}
		String text = text(reply.path("candidates").path(0).path("content").path("parts").path(0).path("text"), "text");
		JsonNode usage = reply.path("usageMetadata");
		return new AiCompletion(text, usage.path("promptTokenCount").asLong(), usage.path("candidatesTokenCount").asLong());
	}

}
