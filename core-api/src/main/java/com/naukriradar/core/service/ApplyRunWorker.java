package com.naukriradar.core.service;

import java.util.ArrayList;
import java.util.List;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.client.MatchForApply;
import com.naukriradar.core.client.MatchingClient;
import com.naukriradar.core.client.UpstreamException;
import com.naukriradar.core.config.ApplicationConfig;
import com.naukriradar.core.config.ApplicationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs an apply run on the bounded pool: fetch matches, plan (under the profile lock), then
 * send the queued ones. Fetching happens before the lock so a slow matching-service never
 * holds a database lock. Its own bean, so {@code @Async} goes through the proxy.
 */
@Component
public class ApplyRunWorker {

	private static final Logger log = LoggerFactory.getLogger(ApplyRunWorker.class);

	private final MatchingClient matchingClient;
	private final ApplyPlanner planner;
	private final ApplyExecutor executor;
	private final ApplyRunStore store;
	private final ApplicationProperties properties;

	public ApplyRunWorker(MatchingClient matchingClient, ApplyPlanner planner, ApplyExecutor executor,
			ApplyRunStore store, ApplicationProperties properties) {
		this.matchingClient = matchingClient;
		this.planner = planner;
		this.executor = executor;
		this.store = store;
		this.properties = properties;
	}

	@Async(ApplicationConfig.APPLY_RUN_POOL)
	public void execute(String runId) {
		try {
			String userId = store.userOf(runId);
			List<MatchForApply> matches = matchingClient.matches(userId, properties.matchLimit());
			ApplyPlanner.PlanResult plan = planner.plan(userId, matches);
			List<String> toSend = new ArrayList<>(executor.requeueDue(userId));
			toSend.addAll(plan.queued());
			store.succeed(runId, plan, executor.process(toSend));
		}
		catch (UpstreamException | NotFoundException ex) {
			store.fail(runId, ex.getMessage());
		}
		catch (RuntimeException ex) {
			log.error("Apply run {} failed", runId, ex);
			store.fail(runId, "Something went wrong while applying (" + ex.getClass().getSimpleName() + ").");
		}
	}

}
