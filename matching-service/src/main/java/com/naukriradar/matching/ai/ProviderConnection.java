package com.naukriradar.matching.ai;

import java.time.Duration;

import com.naukriradar.matching.model.AiProviderType;

/** How to reach one provider account, with its key already decrypted. Never logged. */
public record ProviderConnection(String name, AiProviderType type, String baseUrl, String apiKey, Duration timeout) {

	@Override
	public String toString() {
		return name + " (" + type + ", " + baseUrl + ")";
	}

}
