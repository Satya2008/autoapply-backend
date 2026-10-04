package com.naukriradar.common.events;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * This service's events. Through the gateway each service has its own path:
 * {@code /api/v1/admin/events/<service>/...}.
 */
@RestController
@RequestMapping("/api/v1/admin/events")
public class EventsAdminController {

	private final DeadLetters deadLetters;
	private final OutboxStats outbox;

	public EventsAdminController(DeadLetters deadLetters, OutboxStats outbox) {
		this.deadLetters = deadLetters;
		this.outbox = outbox;
	}

	/** Events that failed every retry, newest first. */
	@GetMapping("/dlq")
	public List<DeadLetters.DeadLetter> deadLetters(@RequestParam(value = "topic", required = false) String topic,
			@RequestParam(value = "limit", defaultValue = "50") int limit) {
		return deadLetters.list(topic, Math.max(1, Math.min(limit, 500)));
	}

	/** Sends a dead letter to its topic again, once the cause is fixed. */
	@PostMapping("/dlq/{id}/replay")
	public DeadLetters.DeadLetter replay(@PathVariable("id") String id) {
		return deadLetters.replay(id);
	}

	@GetMapping("/outbox")
	public OutboxStats.Stats outbox() {
		return outbox.stats();
	}

}
