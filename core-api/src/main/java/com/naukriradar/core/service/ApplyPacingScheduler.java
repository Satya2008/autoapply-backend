package com.naukriradar.core.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.naukriradar.common.events.OutboxWriter;
import com.naukriradar.common.events.Topics;
import com.naukriradar.common.redis.lock.DistributedLock;
import com.naukriradar.common.redis.lock.LockHandle;
import com.naukriradar.common.redis.lock.LockUnavailableException;
import com.naukriradar.core.model.Application;
import com.naukriradar.core.model.ApplicationStatus;
import com.naukriradar.core.repository.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Every few seconds, turns applications whose slot has come into apply.requested events. The
 * event goes through the outbox first and the queue entry is removed after: a crash in
 * between sends it twice at worst, and the worker never submits one attempt twice.
 */
@Component
public class ApplyPacingScheduler implements SmartLifecycle {

	private static final Logger log = LoggerFactory.getLogger(ApplyPacingScheduler.class);

	private static final TypeReference<Map<String, String>> ANSWERS = new TypeReference<>() {
	};

	private final ApplyDelayQueue queue;
	private final ApplicationRepository applications;
	private final OutboxWriter outbox;
	private final DistributedLock lock;
	private final JsonMapper json;
	private final TransactionTemplate transaction;
	private ScheduledExecutorService executor;
	private volatile boolean running;

	public ApplyPacingScheduler(ApplyDelayQueue queue, ApplicationRepository applications, OutboxWriter outbox,
			DistributedLock lock, JsonMapper json, PlatformTransactionManager transactionManager) {
		this.queue = queue;
		this.applications = applications;
		this.outbox = outbox;
		this.lock = lock;
		this.json = json;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	@Override
	public void start() {
		executor = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("apply-pacing").daemon().factory());
		executor.scheduleWithFixedDelay(this::releaseDue, 2, 2, TimeUnit.SECONDS);
		running = true;
	}

	@Override
	public void stop() {
		running = false;
		if (executor != null) {
			executor.shutdownNow();
		}
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	/** @return how many applications were handed out */
	public int releaseDue() {
		Optional<LockHandle> held;
		try {
			held = lock.tryAcquire("apply-pacing");
		}
		catch (LockUnavailableException ex) {
			return 0;
		}
		if (held.isEmpty()) {
			return 0;
		}
		int released = 0;
		try (LockHandle ignored = held.get()) {
			for (String id : queue.due(50)) {
				Boolean sent = transaction.execute(status -> request(id));
				queue.remove(id);
				if (Boolean.TRUE.equals(sent)) {
					released++;
				}
			}
		}
		catch (RuntimeException ex) {
			log.warn("Releasing due applications failed: {}", ex.getMessage());
		}
		return released;
	}

	private boolean request(String applicationId) {
		Application application = applications.findById(applicationId).orElse(null);
		// skipped or answered meanwhile: nothing to send
		if (application == null || application.getStatus() != ApplicationStatus.SENDING) {
			return false;
		}
		Map<String, String> answers = application.getPrefill() == null ? Map.of()
				: json.readValue(application.getPrefill(), ANSWERS);
		outbox.publish(Topics.APPLY_REQUESTED, application.getUserId(), "ApplyRequested",
				new ApplyRequested(application.getId(), application.getUserId(), application.getApplyUrl(),
						application.getAttempts() + 1, answers));
		return true;
	}

	/** Payload of {@link Topics#APPLY_REQUESTED}. */
	public record ApplyRequested(String applicationId, String userId, String applyUrl, int attempt,
			Map<String, String> answers) {
	}

}
