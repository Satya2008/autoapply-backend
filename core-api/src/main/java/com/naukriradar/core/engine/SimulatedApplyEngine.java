package com.naukriradar.core.engine;

import com.naukriradar.core.config.ApplicationProperties.ApplyMode;
import com.naukriradar.core.model.Application;
import com.naukriradar.core.model.ApplicationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Automatic applying is off: nothing is ever sent to an employer. The application is handed
 * to the candidate instead, with their answers ready, so a good match on a site we could apply
 * to doesn't sit unseen in a "practice" state.
 */
@Component
public class SimulatedApplyEngine implements ApplyEngine {

	private static final Logger log = LoggerFactory.getLogger(SimulatedApplyEngine.class);

	public static final String NOTE = "Automatic applying is switched off, so you apply here yourself.";

	@Override
	public ApplyMode mode() {
		return ApplyMode.SIMULATE;
	}

	@Override
	public ApplyResult apply(Application application) {
		log.info("Auto-apply off: application {} to {} handed to the candidate", application.getId(), application.getJobTitle());
		return new ApplyResult(ApplicationStatus.NEEDS_YOU, null, NOTE);
	}

}
