package com.naukriradar.matching.ai;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.naukriradar.matching.model.AiProviderType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** OpenAI Chat Completions API. JSON mode when a schema is asked for. */
@Component
public class OpenAiClient extends HttpAiClient {

	public OpenAiClient(RestClient.Builder builder, JsonMapper json) {
		super(builder, json);
	}

	@Override
	public AiProviderType type() {
		return AiProviderType.OPENAI;
	}

	@Override
	public AiCompletion complete(ProviderConnection provider, String model, AiRequest request) {
		Map<String, Object> body = new HashMap<>();
		body.put("model", model);
		body.put("max_completion_tokens", request.maxTokens());
		body.put("messages", List.of(
				Map.of("role", "system", "content", system(request)),
				Map.of("role", "user", "content", request.prompt())));
		if (request.outputSchema() != null) {
			body.put("response_format", Map.of("type", "json_object"));
		}
		JsonNode reply = client(provider).post()
				.uri("/v1/chat/completions")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.apiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(JsonNode.class);
		if (reply == null) {
			throw new AiProviderException("Empty reply.");
		}
		String text = text(reply.path("choices").path(0).path("message").path("content"), "message");
		JsonNode usage = reply.path("usage");
		return new AiCompletion(text, usage.path("prompt_tokens").asLong(), usage.path("completion_tokens").asLong());
	}

	@Override
	public List<String> listModels(ProviderConnection provider) {
		JsonNode reply = client(provider).get()
				.uri("/v1/models")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + provider.apiKey())
				.retrieve()
				.body(JsonNode.class);
		return ids(reply == null ? null : reply.path("data"), "id");
	}

}
