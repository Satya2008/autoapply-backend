package com.naukriradar.matching.event;

import com.naukriradar.common.events.IdempotentConsumer;
import com.naukriradar.common.events.Topics;
import com.naukriradar.matching.config.SemanticProperties;
import com.naukriradar.matching.service.JobEmbeddingIndexer;
import com.naukriradar.matching.service.RematchCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * jobs.parsed -> embed the new jobs, then rematch everyone who used matching in the last 30
 * days, so new jobs reach them without anyone pressing a button. Each run ends with
 * match.created.
 */
@Component
public class RematchConsumer {

	private static final Logger log = LoggerFactory.getLogger(RematchConsumer.class);

	static final String CONSUMER = "matching-service.rematch";

	private final IdempotentConsumer idempotent;
	private final RematchCoordinator rematch;
	private final JobEmbeddingIndexer indexer;
	private final SemanticProperties semantic;

	public RematchConsumer(IdempotentConsumer idempotent, RematchCoordinator rematch, JobEmbeddingIndexer indexer,
			SemanticProperties semantic) {
		this.idempotent = idempotent;
		this.rematch = rematch;
		this.indexer = indexer;
		this.semantic = semantic;
	}

	@KafkaListener(topics = "#{@eventTopics.name('" + Topics.JOBS_PARSED + "')}", groupId = "#{@eventTopics.group('" + CONSUMER + "')}")
	public void onJobsParsed(String message) {
		// starting a run twice is harmless: a user has at most one running run
		idempotent.handleRepeatable(CONSUMER, message, event -> {
			if (semantic.enabled()) {
				try {
					// before rematching, so the vector search can already find the new jobs
					indexer.catchUp();
				}
				catch (RuntimeException ex) {
					log.warn("Vector index catch-up failed; matching goes on with what is indexed", ex);
				}
			}
			rematch.rematchActiveUsers("New jobs parsed");
		});
	}

}
