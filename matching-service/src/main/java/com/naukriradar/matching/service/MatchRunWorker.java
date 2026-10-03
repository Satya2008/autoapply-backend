package com.naukriradar.matching.service;

import com.naukriradar.matching.client.UpstreamException;
import com.naukriradar.matching.config.AsyncConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs matching on the bounded match pool. It is its own bean on purpose: {@code @Async}
 * works through a proxy, so calling an {@code @Async} method from inside the same class
 * would bypass the proxy and quietly run on the caller's thread.
 */
@Component
public class MatchRunWorker {

	private static final Logger log = LoggerFactory.getLogger(MatchRunWorker.class);

	private final MatchEngine engine;
	private final MatchRunStore store;

	public MatchRunWorker(MatchEngine engine, MatchRunStore store) {
		this.engine = engine;
		this.store = store;
	}

	@Async(AsyncConfig.MATCH_EXECUTOR)
	public void execute(String runId) {
		try {
			store.succeed(runId, engine.match(store.userOf(runId)));
		}
		catch (UpstreamException | MatchInputException ex) {
			store.fail(runId, ex.getMessage());
		}
		catch (RuntimeException ex) {
			log.error("Match run {} failed", runId, ex);
			store.fail(runId, "Something went wrong while matching (" + ex.getClass().getSimpleName() + ").");
		}
	}

}
