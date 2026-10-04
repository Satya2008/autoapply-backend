package com.naukriradar.matching.ai;

import java.util.List;
import java.util.Map;

import com.naukriradar.matching.model.AiProviderType;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Anthropic Messages API. */
@Component
public class AnthropicClient extends HttpAiClient {

	public AnthropicClient(RestClient.Builder builder, JsonMapper json) {
		super(builder, json);
	}

	@Override
	public AiProviderType type() {
		return AiProviderType.ANTHROPIC;
	}

	@Override
	public AiCompletion complete(ProviderConnection provider, String model, AiRequest request) {
		Map<String, Object> body = Map.of(
				"model", model,
				"max_tokens", request.maxTokens(),
				"system", system(request),
				"messages", List.of(Map.of("role", "user", "content", request.prompt())));
		JsonNode reply = client(provider).post()
				.uri("/v1/messages")
				.header("x-api-key", provider.apiKey())
				.header("anthropic-version", "2023-06-01")
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(JsonNode.class);
		if (reply == null) {
			throw new AiProviderException("Empty reply.");
		}
		StringBuilder text = new StringBuilder();
		for (JsonNode block : reply.path("content")) {
			if ("text".equals(block.path("type").asString())) {
				text.append(block.path("text").asString());
			}
		}
		if (text.isEmpty()) {
			throw new AiProviderException("The reply has no text.");
		}
		JsonNode usage = reply.path("usage");
		return new AiCompletion(text.toString(), usage.path("input_tokens").asLong(), usage.path("output_tokens").asLong());
	}

	@Override
	public List<String> listModels(ProviderConnection provider) {
		JsonNode reply = client(provider).get()
				.uri("/v1/models?limit=1000")
				.header("x-api-key", provider.apiKey())
				.header("anthropic-version", "2023-06-01")
				.retrieve()
				.body(JsonNode.class);
		return ids(reply == null ? null : reply.path("data"), "id");
	}

}
