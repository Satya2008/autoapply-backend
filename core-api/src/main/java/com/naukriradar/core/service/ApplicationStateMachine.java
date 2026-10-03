package com.naukriradar.core.service;

import java.time.Clock;

import com.naukriradar.core.model.Application;
import com.naukriradar.core.model.ApplicationEvent;
import com.naukriradar.core.model.ApplicationStatus;
import com.naukriradar.core.repository.ApplicationEventRepository;
import org.springframework.stereotype.Component;

/**
 * The only way an application's status changes: the entity checks the move, this records
 * it in the timeline. Callers must be inside a transaction with the application loaded.
 */
@Component
public class ApplicationStateMachine {

	private final ApplicationEventRepository events;
	private final Clock clock = Clock.systemUTC();

	public ApplicationStateMachine(ApplicationEventRepository events) {
		this.events = events;
	}

	/** Records the creation event; the application must already be saved, so it has an id. */
	public void created(Application application, String note) {
		events.save(new ApplicationEvent(application.getId(), null, application.getStatus(), note, clock.instant()));
	}

	/**
	 * Moves the application. Moving to the status it already has is a no-op that returns
	 * false, which is what makes "mark as done" safe to call twice.
	 *
	 * @throws com.naukriradar.core.exception.IllegalTransitionException if the move isn't allowed
	 */
	public boolean move(Application application, ApplicationStatus next, String note) {
		if (application.getStatus() == next) {
			return false;
		}
		ApplicationStatus previous = application.moveTo(next);
		events.save(new ApplicationEvent(application.getId(), previous, next, note, clock.instant()));
		return true;
	}

}
