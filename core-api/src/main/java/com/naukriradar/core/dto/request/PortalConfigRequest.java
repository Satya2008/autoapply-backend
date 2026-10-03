package com.naukriradar.core.dto.request;

import java.util.Map;

import com.naukriradar.core.model.RiskBand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param domain a host such as "careers.acme.com"; "https://", "www." and any path are removed
 * @param selectors form field name to CSS selector, used by the browser engine (Phase 15)
 */
public record PortalConfigRequest(
		@NotBlank @Size(max = 200) String domain,
		@NotBlank @Size(max = 100) String name,
		@NotNull RiskBand riskBand,
		boolean enabled,
		@Size(max = 50) Map<@NotBlank @Size(max = 50) String, @NotBlank @Size(max = 500) String> selectors) {
}
