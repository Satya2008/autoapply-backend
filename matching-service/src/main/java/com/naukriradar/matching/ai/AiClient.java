package com.naukriradar.matching.ai;

import com.naukriradar.matching.config.AiProperties;

/** Speaks one vendor's API. Stateless: the account to use comes with each call. */
public interface AiClient {

	AiProperties.ProviderType type();

	/**
	 * @throws org.springframework.web.client.RestClientException when the call fails; 5xx,
	 *     429 and network errors are retried by the caller
	 * @throws AiProviderException when the reply can't be read
	 */
	AiCompletion complete(AiProperties.Provider provider, String model, AiRequest request);

}
