package com.naukriradar.core.controller;

import com.naukriradar.core.dto.response.AuditPageResponse;
import com.naukriradar.core.service.AuditService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/audit")
public class AuditAdminController {

	private final AuditService auditService;

	public AuditAdminController(AuditService auditService) {
		this.auditService = auditService;
	}

	/** Newest first. Every filter is optional. */
	@GetMapping
	public AuditPageResponse search(
			@RequestParam(required = false) @Size(max = 100) String actor,
			@RequestParam(required = false) @Size(max = 60) String action,
			@RequestParam(required = false) @Size(max = 40) String targetType,
			@RequestParam(required = false) @Size(max = 40) String cursor,
			@RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit) {
		return auditService.search(actor, action, targetType, cursor, limit);
	}

}
