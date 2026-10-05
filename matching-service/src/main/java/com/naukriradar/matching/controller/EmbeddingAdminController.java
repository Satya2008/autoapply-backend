package com.naukriradar.matching.controller;

import java.util.Map;

import com.naukriradar.matching.dto.response.EmbeddingStatusResponse;
import com.naukriradar.matching.embedding.EmbeddingService;
import com.naukriradar.matching.embedding.HashingEmbedder;
import com.naukriradar.matching.repository.EmbeddingStore;
import com.naukriradar.matching.scheduler.BatchMatchingJob;
import com.naukriradar.matching.service.JobEmbeddingIndexer;
import com.naukriradar.matching.service.JobVectorIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Semantic matching for the admin screen: which embedding model is in use, how many jobs have
 * vectors, and buttons to catch up or run the nightly batch now. The model itself is chosen
 * on the providers (embedding model field).
 */
@RestController
@RequestMapping("/api/v1/admin/ai")
public class EmbeddingAdminController {

	private static final Logger log = LoggerFactory.getLogger(EmbeddingAdminController.class);

	private final EmbeddingService embeddings;
	private final EmbeddingStore store;
	private final JobVectorIndex index;
	private final JobEmbeddingIndexer indexer;
	private final BatchMatchingJob batch;

	public EmbeddingAdminController(EmbeddingService embeddings, EmbeddingStore store, JobVectorIndex index,
			JobEmbeddingIndexer indexer, BatchMatchingJob batch) {
		this.embeddings = embeddings;
		this.store = store;
		this.index = index;
		this.indexer = indexer;
		this.batch = batch;
	}

	@GetMapping("/embeddings")
	public EmbeddingStatusResponse status() {
		String model = embeddings.currentModelKey();
		Map<String, Long> stored = store.countsByModel();
		return new EmbeddingStatusResponse(model, HashingEmbedder.MODEL_KEY.equals(model), stored.getOrDefault(model, 0L),
				stored, index.modelKey().isEmpty() ? null : index.modelKey(), index.size());
	}

	/** Embeds the recent jobs that have no vector for the current model; runs in the background. */
	@PostMapping("/embeddings/reindex")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public Map<String, String> reindex() {
		background("vector-reindex", indexer::catchUp);
		return Map.of("status", "started", "model", embeddings.currentModelKey());
	}

	/** The nightly batch (index catch-up + rematch of active users), now; runs in the background. */
	@PostMapping("/batch-matching/run")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public Map<String, String> runBatch() {
		background("batch-matching", batch::run);
		return Map.of("status", "started");
	}

	private static void background(String name, Runnable work) {
		Thread.ofVirtual().name(name).start(() -> {
			try {
				work.run();
			}
			catch (RuntimeException ex) {
				log.warn("{} failed", name, ex);
			}
		});
	}

}
