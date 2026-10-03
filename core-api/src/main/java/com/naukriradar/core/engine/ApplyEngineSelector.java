package com.naukriradar.core.engine;

import java.util.List;

import com.naukriradar.core.config.ApplicationProperties;
import org.springframework.stereotype.Component;

/**
 * Picks the engine for the configured mode at startup. Asking for a mode with no engine
 * (BROWSER before Phase 15) stops the service, rather than quietly doing something else.
 */
@Component
public class ApplyEngineSelector {

	private final ApplyEngine engine;

	public ApplyEngineSelector(List<ApplyEngine> engines, ApplicationProperties properties) {
		this.engine = engines.stream()
				.filter(e -> e.mode() == properties.mode())
				.findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"No apply engine for naukriradar.applications.mode=" + properties.mode()));
	}

	public ApplyEngine engine() {
		return engine;
	}

}
