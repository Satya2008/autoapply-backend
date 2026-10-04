package com.naukriradar.core.engine;

import java.util.List;

import com.naukriradar.core.config.ApplicationProperties;
import org.springframework.stereotype.Component;

/**
 * Picks the in-process engine for the configured mode at startup. BROWSER mode has none: the
 * apply worker does that work (see {@code ApplyDispatcher}).
 */
@Component
public class ApplyEngineSelector {

	private final ApplyEngine engine;

	public ApplyEngineSelector(List<ApplyEngine> engines, ApplicationProperties properties) {
		this.engine = engines.stream()
				.filter(e -> e.mode() == properties.mode())
				.findFirst()
				.orElse(null);
		if (engine == null && properties.mode() != ApplicationProperties.ApplyMode.BROWSER) {
			throw new IllegalStateException("No apply engine for naukriradar.applications.mode=" + properties.mode());
		}
	}

	/** True when applications go to the apply worker instead of an in-process engine. */
	public boolean worker() {
		return engine == null;
	}

	public ApplyEngine engine() {
		return engine;
	}

}
