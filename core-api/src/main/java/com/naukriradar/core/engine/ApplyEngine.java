package com.naukriradar.core.engine;

import com.naukriradar.core.config.ApplicationProperties.ApplyMode;
import com.naukriradar.core.model.Application;

/** Sends a queued application. Simulate mode today; a browser engine plugs in at Phase 15. */
public interface ApplyEngine {

	ApplyMode mode();

	/**
	 * @throws ApplyFailedException if this attempt didn't go through; it will be retried
	 */
	ApplyResult apply(Application application);

}
