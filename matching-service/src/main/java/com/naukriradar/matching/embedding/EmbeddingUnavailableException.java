package com.naukriradar.matching.embedding;

/** The asked embedding model can't be used right now (not set up any more, or its provider failed). */
public class EmbeddingUnavailableException extends RuntimeException {

	public EmbeddingUnavailableException(String message) {
		super(message);
	}

}
