package com.naukriradar.core.engine;

import com.naukriradar.core.config.ApplicationProperties.ApplyMode;
import com.naukriradar.core.model.Application;
import com.naukriradar.core.model.ApplicationStatus;
import com.naukriradar.core.model.SubmittedVia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Goes through the motions and records the result. Nothing is ever sent to an employer. */
@Component
public class SimulatedApplyEngine implements ApplyEngine {

	private static final Logger log = LoggerFactory.getLogger(SimulatedApplyEngine.class);

	@Override
	public ApplyMode mode() {
		return ApplyMode.SIMULATE;
	}

	@Override
	public ApplyResult apply(Application application) {
		log.info("Simulated application {} to {} at {}", application.getId(), application.getJobTitle(),
				application.getApplyUrl());
		return new ApplyResult(ApplicationStatus.SIMULATED, SubmittedVia.SIMULATED,
				"Simulated: nothing was sent to the employer.");
	}

}
