package com.naukriradar.matching.service;

import com.naukriradar.common.redis.run.RunLeases;
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
	private final MatchCache cache;
	private final RunLeases leases;

	public MatchRunWorker(MatchEngine engine, MatchRunStore store, MatchCache cache, RunLeases leases) {
		this.engine = engine;
		this.store = store;
		this.cache = cache;
		this.leases = leases;
	}

	/** The run's lease was taken when it was created; it is given back here, whatever happens. */
	@Async(AsyncConfig.MATCH_EXECUTOR)
	public void execute(String runId) {
		String userId = null;
		try {
			userId = store.userOf(runId);
			store.succeed(runId, engine.match(userId));
		}
		catch (UpstreamException | MatchInputException ex) {
			store.fail(runId, ex.getMessage());
		}
		catch (RuntimeException ex) {
			log.error("Match run {} failed", runId, ex);
			store.fail(runId, "Something went wrong while matching (" + ex.getClass().getSimpleName() + ").");
		}
		finally {
			// matches are written as the run goes, so drop the pages even if it failed halfway;
			// and only after the commit, so nobody caches the old page again in between
			if (userId != null) {
				cache.forgetUser(userId);
			}
			leases.end(MatchRunStore.LEASE, runId);
		}
	}

}
