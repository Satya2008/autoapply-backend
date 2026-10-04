package com.naukriradar.common.resilience;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Which dependencies of this service are healthy. Through the gateway:
 * {@code /api/v1/admin/resilience/<service>}. Counts are per instance.
 */
@RestController
@RequestMapping("/api/v1/admin/resilience")
public class ResilienceAdminController {

	private final Resilience resilience;

	public ResilienceAdminController(Resilience resilience) {
		this.resilience = resilience;
	}

	@GetMapping
	public List<Resilience.DependencyState> states() {
		return resilience.states();
	}

}
