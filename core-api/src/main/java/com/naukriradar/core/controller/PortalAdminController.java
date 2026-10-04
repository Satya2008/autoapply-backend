package com.naukriradar.core.controller;

import java.util.List;

import com.naukriradar.core.dto.request.PortalConfigRequest;
import com.naukriradar.core.dto.request.PortalDryRunRequest;
import com.naukriradar.core.dto.response.PortalConfigResponse;
import com.naukriradar.core.service.PortalConfigService;
import com.naukriradar.core.service.PortalDryRunService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** Apply portals: risk overrides now, browser selectors in Phase 15. Admin-only from Phase 8. */
@RestController
@RequestMapping("/api/v1/admin/portals")
public class PortalAdminController {

	private final PortalConfigService service;
	private final PortalDryRunService dryRuns;

	public PortalAdminController(PortalConfigService service, PortalDryRunService dryRuns) {
		this.service = service;
		this.dryRuns = dryRuns;
	}

	@GetMapping
	public List<PortalConfigResponse> list() {
		return service.list();
	}

	@GetMapping("/{id}")
	public PortalConfigResponse get(@PathVariable String id) {
		return service.get(id);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public PortalConfigResponse create(@Valid @RequestBody PortalConfigRequest request) {
		return service.create(request);
	}

	@PutMapping("/{id}")
	public PortalConfigResponse update(@PathVariable String id, @Valid @RequestBody PortalConfigRequest request) {
		return service.update(id, request);
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable String id) {
		service.delete(id);
	}

	/**
	 * Tries this portal's selectors on a real form: fills it with clearly fake sample answers
	 * in the apply worker's browser and submits nothing. Shows which fields were found.
	 */
	@PostMapping("/{id}/dry-run")
	public JsonNode dryRun(@PathVariable String id, @Valid @RequestBody PortalDryRunRequest request) {
		return dryRuns.dryRun(id, request.url());
	}

}
