package com.naukriradar.matching.scheduler;

import java.time.Duration;
import java.util.Optional;

import com.naukriradar.common.redis.lock.DistributedLock;
import com.naukriradar.common.redis.lock.LockHandle;
import com.naukriradar.common.redis.lock.LockUnavailableException;
import com.naukriradar.matching.config.SemanticProperties;
import com.naukriradar.matching.service.JobEmbeddingIndexer;
import com.naukriradar.matching.service.RematchCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The nightly batch, when nobody is waiting: bring every recent job's vector up to date, then
 * rematch all active users. Day-time rematches only follow new jobs; this one also refreshes
 * recency and picks up profile changes. AI answers are cached by prompt and text, so
 * unchanged matches cost nothing to re-review.
 *
 * <p>Runs on one instance: the lock is kept for a while after it ends, so an instance whose
 * clock is a few seconds behind doesn't run the same night again.
 */
@Component
public class BatchMatchingJob {

	private static final Logger log = LoggerFactory.getLogger(BatchMatchingJob.class);

	static final String LOCK = "nightly-matching";

	private static final Duration MIN_HOLD = Duration.ofMinutes(5);

	private final DistributedLock locks;
	private final JobEmbeddingIndexer indexer;
	private final RematchCoordinator rematch;
	private final SemanticProperties semantic;

	public BatchMatchingJob(DistributedLock locks, JobEmbeddingIndexer indexer, RematchCoordinator rematch,
			SemanticProperties semantic) {
		this.locks = locks;
		this.indexer = indexer;
		this.rematch = rematch;
		this.semantic = semantic;
	}

	@Scheduled(cron = "${naukriradar.semantic.nightly-cron:0 30 2 * * *}", zone = "${naukriradar.semantic.nightly-zone:Asia/Kolkata}")
	public void runNightly() {
		Optional<LockHandle> lock;
		try {
			lock = locks.tryAcquire(LOCK, Duration.ofMinutes(10));
		}
		catch (LockUnavailableException ex) {
			log.warn("Nightly matching skipped: Redis is unavailable");
			return;
		}
		if (lock.isEmpty()) {
			return;
		}
		LockHandle handle = lock.get();
		try {
			run();
		}
		finally {
			handle.releaseAfter(MIN_HOLD);
		}
	}

	/** The batch itself, without the lock: also what the admin "run now" calls. */
	public Result run() {
		JobEmbeddingIndexer.CatchUp index = semantic.enabled() ? indexer.catchUp() : null;
		RematchCoordinator.Round round = rematch.rematchActiveUsers("Nightly batch");
		return new Result(index, round);
	}

	/** @param index null when semantic matching is off */
	public record Result(JobEmbeddingIndexer.CatchUp index, RematchCoordinator.Round rematch) {
	}

}
