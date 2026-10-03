package com.naukriradar.core.mapper;

import java.util.LinkedHashMap;

import com.naukriradar.core.dto.request.PortalConfigRequest;
import com.naukriradar.core.dto.response.PortalConfigResponse;
import com.naukriradar.core.model.PortalConfig;
import org.springframework.stereotype.Component;

@Component
public class PortalConfigMapper {

	public void apply(PortalConfigRequest request, PortalConfig portal) {
		portal.setName(request.name().strip());
		portal.setRiskBand(request.riskBand());
		portal.setEnabled(request.enabled());
		portal.getSelectors().clear();
		if (request.selectors() != null) {
			request.selectors().forEach((field, selector) -> portal.getSelectors().put(field.strip(), selector.strip()));
		}
	}

	public PortalConfigResponse toResponse(PortalConfig portal) {
		return new PortalConfigResponse(portal.getId(), portal.getDomain(), portal.getName(), portal.getRiskBand(),
				portal.isEnabled(), new LinkedHashMap<>(portal.getSelectors()), portal.getCreatedAt());
	}

}
