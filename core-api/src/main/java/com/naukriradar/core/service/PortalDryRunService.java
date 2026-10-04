package com.naukriradar.core.service;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.core.client.ApplyWorkerClient;
import com.naukriradar.core.dto.response.PortalConfigResponse;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

@Service
public class PortalDryRunService {

	private final PortalConfigService portals;
	private final ApplyWorkerClient worker;

	public PortalDryRunService(PortalConfigService portals, ApplyWorkerClient worker) {
		this.portals = portals;
		this.worker = worker;
	}

	public JsonNode dryRun(String portalId, String url) {
		PortalConfigResponse portal = portals.get(portalId);
		String host = RiskClassifier.host(url);
		if (host == null || !RiskClassifier.covers(portal.domain(), host)) {
			throw new BadRequestException("The URL must be on " + portal.domain() + ".");
		}
		if (portal.selectors() == null || portal.selectors().isEmpty()) {
			throw new BadRequestException("Add the form's selectors to this portal first.");
		}
		return worker.dryRun(url, portal.selectors());
	}

}
