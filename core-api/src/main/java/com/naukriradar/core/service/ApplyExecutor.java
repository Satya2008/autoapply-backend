package com.naukriradar.core.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.naukriradar.core.config.ApplicationProperties;
import com.naukriradar.core.engine.ApplyEngineSelector;
import com.naukriradar.core.engine.ApplyFailedException;
import com.naukriradar.core.engine.ApplyResult;
import com.naukriradar.core.model.Application;
import com.naukriradar.core.model.ApplicationStatus;
import com.naukriradar.core.repository.ApplicationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends queued applications through the engine, one transaction per application so one
 * failure never undoes the others. A failed attempt is retried later with a growing delay;
 * after the last attempt the application is handed to the user instead of being dropped.
 */
@Service
public class ApplyExecutor {

	private static final Logger log = LoggerFactory.getLogger(ApplyExecutor.class);

	private final ApplicationRepository repository;
	private final ApplicationStateMachine stateMachine;
	private final ApplyEngineSelector engines;
	private final ApplicationProperties properties;
	private final TransactionTemplate transaction;
	private final Clock clock = Clock.systemUTC();

	public ApplyExecutor(ApplicationRepository repository, ApplicationStateMachine stateMachine,
			ApplyEngineSelector engines, ApplicationProperties properties, PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.stateMachine = stateMachine;
		this.engines = engines;
		this.properties = properties;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/** Failed applications whose retry time has come, put back in the queue. */
	public List<String> requeueDue(String userId) {
		List<String> ids = new ArrayList<>();
		transaction.executeWithoutResult(status -> {
			for (Application application : repository.findByUserIdAndStatusAndNextAttemptAtLessThanEqual(userId,
					ApplicationStatus.FAILED, clock.instant())) {
				stateMachine.move(application, ApplicationStatus.QUEUED, "Retrying (attempt " + (application.getAttempts() + 1) + ").");
				ids.add(application.getId());
			}
		});
		return ids;
	}

	public Outcome process(List<String> applicationIds) {
		int sent = 0;
		int failed = 0;
		int handedOver = 0;
		for (String id : applicationIds) {
			Result result = transaction.execute(status -> processOne(id));
			if (result == Result.SENT) {
				sent++;
			}
			else if (result == Result.FAILED) {
				failed++;
			}
			else if (result == Result.HANDED_OVER) {
				handedOver++;
			}
		}
		return new Outcome(sent, failed, handedOver);
	}

	private Result processOne(String id) {
		Application application = repository.findById(id).orElse(null);
		// someone may have skipped it, or another run already sent it
		if (application == null || application.getStatus() != ApplicationStatus.QUEUED) {
			return Result.UNCHANGED;
		}
		try {
			ApplyResult result = engines.engine().apply(application);
			stateMachine.move(application, result.status(), result.note());
			application.submittedVia(result.via());
			return Result.SENT;
		}
		catch (ApplyFailedException ex) {
			Instant now = clock.instant();
			int attempt = application.getAttempts() + 1;
			if (attempt >= properties.maxAttempts()) {
				application.recordFailure(ex.getMessage(), null);
				String reason = "Automatic apply failed " + attempt + " times (" + ex.getMessage() + "). Please apply yourself.";
				stateMachine.move(application, ApplicationStatus.NEEDS_YOU, reason);
				application.needsYouBecause(reason);
				return Result.HANDED_OVER;
			}
			Duration wait = properties.retryDelay().multipliedBy(1L << (attempt - 1));
			application.recordFailure(ex.getMessage(), now.plus(wait));
			stateMachine.move(application, ApplicationStatus.FAILED,
					"Attempt " + attempt + " failed: " + ex.getMessage() + ". Retrying after " + wait.toMinutes() + " minutes.");
			log.info("Application {} failed attempt {}: {}", id, attempt, ex.getMessage());
			return Result.FAILED;
		}
	}

	private enum Result {
		SENT, FAILED, HANDED_OVER, UNCHANGED
	}

	/**
	 * @param handedOver gave up on automation and moved to NEEDS_YOU
	 */
	public record Outcome(int sent, int failed, int handedOver) {
	}

}
