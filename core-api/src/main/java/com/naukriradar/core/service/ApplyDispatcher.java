package com.naukriradar.core.service;

import com.naukriradar.core.model.Application;
import com.naukriradar.core.model.ApplicationStatus;
import org.springframework.stereotype.Component;

/**
 * Browser mode: instead of applying here, hand the application to the apply worker. It moves
 * to SENDING (so no other run picks it up again) and waits in the delay queue for its slot.
 */
@Component
public class ApplyDispatcher {

	private final ApplicationStateMachine stateMachine;
	private final ApplyDelayQueue queue;

	public ApplyDispatcher(ApplicationStateMachine stateMachine, ApplyDelayQueue queue) {
		this.stateMachine = stateMachine;
		this.queue = queue;
	}

	/** Must run in the caller's transaction, with the application loaded in it. */
	public void dispatch(Application application) {
		stateMachine.move(application, ApplicationStatus.SENDING, "Handed to the apply worker.");
		queue.schedule(application.getId(), application.getUserId());
	}

}
