package com.naukriradar.core.client;

import tools.jackson.databind.JsonNode;

/**
 * matching-service's AI answer.
 *
 * @param answer the checked JSON answer, or null
 * @param unavailableReason why there is no answer (AI off, budget spent, every provider down)
 * @param answeredBy provider:model that answered, or null
 */
public record AiReply(JsonNode answer, String unavailableReason, String answeredBy) {

	public boolean answered() {
		return answer != null && !answer.isNull();
	}

}
