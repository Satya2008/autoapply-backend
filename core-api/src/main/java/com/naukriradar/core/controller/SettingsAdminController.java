package com.naukriradar.core.controller;

import java.util.List;

import com.naukriradar.core.audit.RequestOrigin;
import com.naukriradar.core.dto.request.SettingUpdateRequest;
import com.naukriradar.core.dto.response.SettingResponse;
import com.naukriradar.core.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Runtime settings: changes apply on the next read, no restart. Admin-only from Phase 8. */
@RestController
@RequestMapping("/api/v1/admin/settings")
public class SettingsAdminController {

	private final SettingsService settingsService;

	public SettingsAdminController(SettingsService settingsService) {
		this.settingsService = settingsService;
	}

	@GetMapping
	public List<SettingResponse> list(@RequestParam(required = false) String category) {
		return settingsService.list(category);
	}

	@GetMapping("/{key}")
	public SettingResponse get(@PathVariable String key) {
		return settingsService.get(key);
	}

	@PutMapping("/{key}")
	public SettingResponse update(@PathVariable String key, @Valid @RequestBody SettingUpdateRequest request) {
		return settingsService.update(key, request.value(), RequestOrigin.current().actor());
	}

	/** Back to the default. */
	@PostMapping("/{key}/reset")
	public SettingResponse reset(@PathVariable String key) {
		return settingsService.reset(key, RequestOrigin.current().actor());
	}

}
