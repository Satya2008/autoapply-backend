package com.naukriradar.core.controller;

import com.naukriradar.core.dto.response.PortalConfigResponse;
import com.naukriradar.core.service.PortalConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** For the apply worker: how to fill a portal's form. Not routed by the gateway. */
@RestController
@RequestMapping("/internal/v1/portals")
public class InternalPortalController {

	private final PortalConfigService portals;

	public InternalPortalController(PortalConfigService portals) {
		this.portals = portals;
	}

	@GetMapping
	public PortalConfigResponse forDomain(@RequestParam String domain) {
		return portals.forHost(domain);
	}

}
