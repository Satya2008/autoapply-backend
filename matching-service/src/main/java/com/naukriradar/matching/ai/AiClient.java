package com.naukriradar.matching.ai;

import java.util.List;

import com.naukriradar.matching.model.AiProviderType;

/** Speaks one vendor's API. Stateless: the account to use comes with each call. */
public interface AiClient {

	AiProviderType type();

	/**
	 * @throws org.springframework.web.client.RestClientException when the call fails; 5xx,
	 *     429 and network errors are retried by the caller
	 * @throws AiProviderException when the reply can't be read
	 */
	AiCompletion complete(ProviderConnection provider, String model, AiRequest request);

	/** The models this account can use, as the vendor lists them. */
	List<String> listModels(ProviderConnection provider);

	/**
	 * One vector per text, in the same order.
	 *
	 * @throws AiProviderException when this API has no embeddings or the reply can't be read
	 */
	default AiEmbeddings embed(ProviderConnection provider, String model, List<String> texts) {
		throw new AiProviderException(type().label() + " has no embeddings API.");
	}

}
