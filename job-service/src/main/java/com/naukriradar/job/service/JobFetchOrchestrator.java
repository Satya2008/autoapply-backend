package com.naukriradar.job.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.ServiceUnavailableException;
import com.naukriradar.common.redis.lock.DistributedLock;
import com.naukriradar.common.redis.lock.LockHandle;
import com.naukriradar.common.redis.lock.LockUnavailableException;
import com.naukriradar.common.redis.run.RunLeases;
import com.naukriradar.job.dto.response.FetchResultResponse;
import com.naukriradar.job.dto.response.FetchRunResponse;
import com.naukriradar.job.model.RunStatus;
import com.naukriradar.job.model.RunTrigger;
import com.naukriradar.job.repository.JobSourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Fetches every enabled source at once, one virtual thread per source. The work is almost
 * all waiting on HTTP, so a thread per source costs next to nothing and the run takes as
 * long as the slowest board, not the sum of all of them. Each source has a deadline; a
 * source that misses it is cancelled and reported, and the others are unaffected.
 *
 * <p>Runs happen in the background: {@link #start} returns at once with the run id, and the
 * caller polls the run for the result. Only one run at a time across all instances: a Redis
 * lock held for the whole run. The run also holds a lease, so a sweep on any instance can
 * tell a live run from one whose process died.
 */
@Service
public class JobFetchOrchestrator {

	private static final Logger log = LoggerFactory.getLogger(JobFetchOrchestrator.class);

	/** Added to each source's own budget (timeout x pages) to cover parsing and saving. */
	private static final Duration DEADLINE_MARGIN = Duration.ofSeconds(10);

	private static final String STARTING = "starting";

	static final String RUN_LOCK = "fetch-run";

	private final JobSourceRepository sourceRepository;
	private final JobIngestService ingestService;
	private final FetchRunService runService;
	private final DistributedLock lock;
	private final RunLeases leases;
	private final Clock clock = Clock.systemUTC();

	private final AtomicReference<String> currentRun = new AtomicReference<>();

	public JobFetchOrchestrator(JobSourceRepository sourceRepository, JobIngestService ingestService,
			FetchRunService runService, DistributedLock lock, RunLeases leases) {
		this.sourceRepository = sourceRepository;
		this.ingestService = ingestService;
		this.runService = runService;
		this.lock = lock;
		this.leases = leases;
	}

	/**
	 * @throws ConflictException if a run is going, here or on another instance
	 * @throws ServiceUnavailableException if Redis is down, so we can't tell
	 */
	public FetchRunResponse start(RunTrigger trigger) {
		if (!currentRun.compareAndSet(null, STARTING)) {
			throw new ConflictException("A fetch run is already in progress (" + currentRun.get() + ").");
		}
		LockHandle runLock;
		try {
			runLock = lock.tryAcquire(RUN_LOCK).orElse(null);
		}
		catch (LockUnavailableException ex) {
			currentRun.set(null);
			throw new ServiceUnavailableException("Can't start a fetch run: the lock service is unreachable.");
		}
		if (runLock == null) {
			currentRun.set(null);
			throw new ConflictException("A fetch run is already in progress on another instance.");
		}
		String runId = null;
		try {
			runId = runService.start(trigger, clock.instant());
			currentRun.set(runId);
			leases.begin(FetchRunService.LEASE, runId);
			String id = runId;
			Thread.ofVirtual().name("fetch-run-" + runId).start(() -> execute(id, runLock));
			return runService.get(runId);
		}
		catch (RuntimeException ex) {
			if (runId != null) {
				leases.end(FetchRunService.LEASE, runId);
			}
			runLock.close();
			currentRun.set(null);
			throw ex;
		}
	}

	public boolean isRunning() {
		return currentRun.get() != null;
	}

	private void execute(String runId, LockHandle runLock) {
		try {
			List<SourceRef> sources = sourceRepository.findByEnabledTrueOrderByPriorityDescCodeAsc().stream()
					.map(s -> new SourceRef(s.getId(), s.getCode(),
							Duration.ofSeconds((long) s.getTimeoutSeconds() * s.getMaxPages()).plus(DEADLINE_MARGIN)))
					.toList();
			runService.finish(runId, runAll(sources), clock.instant());
		}
		catch (RuntimeException ex) {
			log.error("Fetch run {} failed", runId, ex);
			runService.fail(runId, "Run failed: " + ex.getClass().getSimpleName(), clock.instant());
		}
		finally {
			leases.end(FetchRunService.LEASE, runId);
			runLock.close();
			currentRun.set(null);
		}
	}

	private List<FetchResultResponse> runAll(List<SourceRef> sources) {
		ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
		try {
			Instant started = clock.instant();
			Map<SourceRef, Future<FetchResultResponse>> futures = new LinkedHashMap<>();
			for (SourceRef source : sources) {
				futures.put(source, pool.submit(() -> ingestService.fetch(source.id())));
			}
			List<FetchResultResponse> results = new ArrayList<>();
			for (Map.Entry<SourceRef, Future<FetchResultResponse>> entry : futures.entrySet()) {
				results.add(await(entry.getKey(), entry.getValue(), started));
			}
			return results;
		}
		finally {
			// don't wait for cancelled stragglers; each one cleans up its own lock and status
			pool.shutdownNow();
		}
	}

	private FetchResultResponse await(SourceRef source, Future<FetchResultResponse> future, Instant started) {
		Duration remaining = Duration.between(clock.instant(), started.plus(source.budget()));
		try {
			return future.get(Math.max(0, remaining.toMillis()), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			future.cancel(true);
			return failed(source, "Timed out after " + source.budget().toSeconds() + "s.", started);
		}
		catch (ExecutionException ex) {
			if (ex.getCause() instanceof ConflictException) {
				return failed(source, "Skipped: a manual fetch of this source was already running.", started);
			}
			if (ex.getCause() instanceof ServiceUnavailableException) {
				return failed(source, "Skipped: " + ex.getCause().getMessage(), started);
			}
			log.error("Source {} failed unexpectedly in a fetch run", source.code(), ex.getCause());
			return failed(source, "Unexpected error: " + ex.getCause().getClass().getSimpleName(), started);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			future.cancel(true);
			return failed(source, "Interrupted.", started);
		}
	}

	private FetchResultResponse failed(SourceRef source, String message, Instant started) {
		return new FetchResultResponse(source.code(), RunStatus.FAILED, 0, 0, 0, 0, 0, 0, message, false,
				Duration.between(started, clock.instant()).toMillis());
	}

	private record SourceRef(String id, String code, Duration budget) {
	}

}
