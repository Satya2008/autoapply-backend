package com.naukriradar.worker.client;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.naukriradar.worker.config.ServicesProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

/**
 * Asks core-api to answer a form's questions for a user: from the profile where it can, else
 * by AI from the resume. Questions it can't answer come back without an answer.
 */
@Component
public class AnswersClient {

	private final RestClient client;

	public AnswersClient(RestClient.Builder builder, ServicesProperties properties) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1).connectTimeout(properties.connectTimeout()).build());
		// AI answers a batch of questions in one call, and a busy model is retried; give it time
		factory.setReadTimeout(Duration.ofSeconds(150));
		this.client = builder.clone().requestFactory(factory).baseUrl(properties.coreApiUrl()).build();
	}

	/** @return question -> answer, for the questions that got one */
	public Map<String, String> answer(String userId, String applicationId, List<String> questions) {
		JsonNode reply = client.post()
				.uri("/internal/v1/users/{userId}/applications/{applicationId}/answers", userId, applicationId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("questions", questions))
				.retrieve()
				.body(JsonNode.class);
		Map<String, String> answers = new LinkedHashMap<>();
		if (reply != null) {
			for (JsonNode item : reply.path("answers")) {
				String answer = item.path("answer").isString() ? item.path("answer").asString() : null;
				if (answer != null && !answer.isBlank()) {
					answers.put(item.path("question").asString(), answer);
				}
			}
		}
		return answers;
	}

}
