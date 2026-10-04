package com.naukriradar.worker.event;

import java.util.List;
import java.util.Optional;

import com.naukriradar.common.events.IdempotentConsumer;
import com.naukriradar.common.events.OutboxWriter;
import com.naukriradar.common.events.Topics;
import com.naukriradar.worker.browser.BrowserApplyEngine;
import com.naukriradar.worker.browser.BrowserApplyEngine.FormResult;
import com.naukriradar.worker.browser.BrowserApplyEngine.Outcome;
import com.naukriradar.worker.client.PortalConfig;
import com.naukriradar.worker.client.PortalConfigClient;
import com.naukriradar.worker.service.ApplyAttempts;
import com.naukriradar.worker.service.ScreenshotStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * apply.requested -> fill and submit the form -> apply.completed. The topic is keyed by user,
 * and each listener thread owns its partitions, so one user's applications go out one after
 * the other; as many threads as browsers.
 */
@Component
public class ApplyRequestedConsumer {

	private static final Logger log = LoggerFactory.getLogger(ApplyRequestedConsumer.class);

	static final String CONSUMER = "apply-worker.apply";

	private final IdempotentConsumer idempotent;
	private final ApplyAttempts attempts;
	private final PortalConfigClient portals;
	private final BrowserApplyEngine engine;
	private final ScreenshotStore screenshots;
	private final OutboxWriter outbox;
	private final JsonMapper json;
	private final TransactionTemplate transaction;

	public ApplyRequestedConsumer(IdempotentConsumer idempotent, ApplyAttempts attempts, PortalConfigClient portals,
			BrowserApplyEngine engine, ScreenshotStore screenshots, OutboxWriter outbox, JsonMapper json,
			PlatformTransactionManager transactionManager) {
		this.idempotent = idempotent;
		this.attempts = attempts;
		this.portals = portals;
		this.engine = engine;
		this.screenshots = screenshots;
		this.outbox = outbox;
		this.json = json;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	@KafkaListener(topics = "#{@eventTopics.name('" + Topics.APPLY_REQUESTED + "')}",
			groupId = "#{@eventTopics.group('" + CONSUMER + "')}", concurrency = "${naukriradar.browser.pool-size:1}")
	public void onApplyRequested(String message) {
		idempotent.handleRepeatable(CONSUMER, message, event -> {
			ApplyRequested request = json.treeToValue(event.payload(), ApplyRequested.class);
			Optional<String> earlier = attempts.begin(request.applicationId(), request.attempt());
			if (earlier.isPresent()) {
				if ("STARTED".equals(earlier.get())) {
					// we may have died right after pressing submit: never press it twice
					report(request, new FormResult(Outcome.UNKNOWN, List.of(), List.of(),
							"The worker stopped in the middle of this attempt; check on the site whether it went through.",
							null));
				}
				return;
			}
			report(request, attempt(request));
		});
	}

	private FormResult attempt(ApplyRequested request) {
		Optional<PortalConfig> portal = portals.forUrl(request.applyUrl());
		if (portal.isEmpty() || portal.get().selectors().isEmpty()) {
			return new FormResult(Outcome.NEEDS_YOU, List.of(), List.of(),
					"No form mapping is set up for this site yet.", null);
		}
		log.info("Applying {} (attempt {}) on {}", request.applicationId(), request.attempt(), portal.get().name());
		return engine.apply(request.applyUrl(), portal.get().selectors(), request.answers(), true);
	}

	private void report(ApplyRequested request, FormResult result) {
		String screenshot = screenshots.save(request.applicationId(), request.attempt(), result.screenshot());
		transaction.executeWithoutResult(status -> {
			attempts.finish(request.applicationId(), request.attempt(), result.outcome().name());
			outbox.publish(Topics.APPLY_COMPLETED, request.userId(), ApplicationAttemptFinished.TYPE,
					new ApplicationAttemptFinished(request.applicationId(), request.userId(), request.attempt(),
							result.outcome().name(), result.note(), result.filled(), result.missing(), screenshot));
		});
	}

}
