package com.naukriradar.matching.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.naukriradar.matching.model.AiProviderType;
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
	public AiProviderType type() {
		return AiProviderType.GEMINI;
	}

	@Override
	public AiCompletion complete(ProviderConnection provider, String model, AiRequest request) {
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

	/** batchEmbedContents: one request per text, answered in the same order. */
	@Override
	public AiEmbeddings embed(ProviderConnection provider, String model, List<String> texts) {
		String name = "models/" + model;
		List<Map<String, Object>> requests = texts.stream()
				.<Map<String, Object>>map(text -> Map.of("model", name, "content", Map.of("parts", List.of(Map.of("text", text)))))
				.toList();
		JsonNode reply = client(provider).post()
				.uri("/v1beta/models/{model}:batchEmbedContents", model)
				.header("x-goog-api-key", provider.apiKey())
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("requests", requests))
				.retrieve()
				.body(JsonNode.class);
		if (reply == null) {
			throw new AiProviderException("Empty reply.");
		}
		List<float[]> vectors = new ArrayList<>();
		for (JsonNode embedding : reply.path("embeddings")) {
			vectors.add(vector(embedding.path("values")));
		}
		checkCount(vectors, texts);
		// Gemini doesn't report embedding tokens
		return new AiEmbeddings(vectors, 0);
	}

	/** Only models that can generate text; names come as "models/x" and are given back as "x". */
	@Override
	public List<String> listModels(ProviderConnection provider) {
		JsonNode reply = client(provider).get()
				.uri("/v1beta/models?pageSize=1000")
				.header("x-goog-api-key", provider.apiKey())
				.retrieve()
				.body(JsonNode.class);
		List<String> models = new ArrayList<>();
		if (reply != null) {
			for (JsonNode model : reply.path("models")) {
				boolean generates = false;
				for (JsonNode method : model.path("supportedGenerationMethods")) {
					generates |= "generateContent".equals(method.asString());
				}
				if (generates) {
					models.add(model.path("name").asString().replaceFirst("^models/", ""));
				}
			}
		}
		return models.stream().sorted().toList();
	}

}
