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

/** A local Ollama server: free, for development. Ollama enforces the schema itself. */
@Component
public class OllamaClient extends HttpAiClient {

	public OllamaClient(RestClient.Builder builder, JsonMapper json) {
		super(builder, json);
	}

	@Override
	public AiProperties.ProviderType type() {
		return AiProperties.ProviderType.OLLAMA;
	}

	@Override
	public AiCompletion complete(AiProperties.Provider provider, String model, AiRequest request) {
		Map<String, Object> body = new HashMap<>();
		body.put("model", model);
		body.put("stream", false);
		body.put("options", Map.of("num_predict", request.maxTokens()));
		body.put("messages", List.of(
				Map.of("role", "system", "content", system(request)),
				Map.of("role", "user", "content", request.prompt())));
		if (request.outputSchema() != null) {
			body.put("format", request.outputSchema());
		}
		JsonNode reply = client(provider).post()
				.uri("/api/chat")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(JsonNode.class);
		if (reply == null) {
			throw new AiProviderException("Empty reply.");
		}
		String text = text(reply.path("message").path("content"), "message");
		return new AiCompletion(text, reply.path("prompt_eval_count").asLong(), reply.path("eval_count").asLong());
	}

}
