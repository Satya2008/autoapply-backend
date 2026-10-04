package com.naukriradar.common.redis.run;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import com.naukriradar.common.redis.lock.LockUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Before leases, a run left RUNNING was closed only when the service restarted, which with
 * two instances would also close the runs the other one was still working on. Now every
 * instance sweeps now and then, and only runs without a lease are closed.
 */
public class InterruptedRunSweeper implements AutoCloseable {

	private static final Logger log = LoggerFactory.getLogger(InterruptedRunSweeper.class);

	private final List<InterruptedRunCloser> closers;
	private final ScheduledExecutorService executor;
	private final Duration interval;
	private volatile ScheduledFuture<?> task;

	public InterruptedRunSweeper(List<InterruptedRunCloser> closers, ScheduledExecutorService executor, Duration interval) {
		this.closers = closers;
		this.executor = executor;
		this.interval = interval;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void start() {
		if (closers.isEmpty() || task != null) {
			return;
		}
		task = executor.scheduleWithFixedDelay(this::sweep, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
	}

	public void sweep() {
		for (InterruptedRunCloser closer : closers) {
			try {
				closer.closeInterruptedRuns();
			}
			catch (LockUnavailableException ex) {
				log.debug("Skipping the run sweep: {}", ex.getMessage());
			}
			catch (RuntimeException ex) {
				log.warn("Run sweep failed in {}", closer.getClass().getSimpleName(), ex);
			}
		}
	}

	@Override
	public void close() {
		ScheduledFuture<?> current = task;
		if (current != null) {
			current.cancel(false);
		}
	}

}
