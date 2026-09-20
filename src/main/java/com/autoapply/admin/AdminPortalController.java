package com.autoapply.admin;

import com.autoapply.apply.portal.ApplyPortalConfig;
import com.autoapply.apply.portal.ApplyPortalConfigRepository;
import com.autoapply.audit.AuditAction;
import com.autoapply.audit.AuditService;
import com.autoapply.common.ApiResponse;
import com.autoapply.common.AppException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/portals")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Admin · Apply Portals",
        description = "Selectors that teach the browser engine how to fill each site's form.")
public class AdminPortalController {

    private final ApplyPortalConfigRepository repository;
    private final AuditService auditService;

    @GetMapping
    public ApiResponse<List<ApplyPortalConfig>> list() {
        return ApiResponse.ok(repository.findAllByOrderByPriorityAscNameAsc());
    }

    @GetMapping("/{id}")
    public ApiResponse<ApplyPortalConfig> get(@PathVariable String id) {
        return ApiResponse.ok(repository.findById(id).orElseThrow(() -> AppException.notFound("Portal config")));
    }

    @GetMapping("/field-reference")
    @Operation(summary = "The candidate fields a selector mapping can pull values from")
    public ApiResponse<Map<String, String>> fieldReference() {
        return ApiResponse.ok(Map.ofEntries(
                Map.entry("fullName", "Candidate's full name"),
                Map.entry("firstName", "First word of the full name"),
                Map.entry("lastName", "Remainder of the full name"),
                Map.entry("email", "Account email"),
                Map.entry("phone", "Phone number"),
                Map.entry("location", "Current location"),
                Map.entry("currentRole", "Current job title"),
                Map.entry("experienceYears", "Years of experience"),
                Map.entry("expectedSalary", "Expected salary"),
                Map.entry("noticePeriod", "Notice period"),
                Map.entry("linkedinUrl", "LinkedIn profile"),
                Map.entry("githubUrl", "GitHub profile"),
                Map.entry("portfolioUrl", "Portfolio site"),
                Map.entry("skills", "Comma separated skills"),
                Map.entry("coverLetter", "Generated or saved cover letter"),
                Map.entry("resumeFile", "Absolute path to the uploaded resume, for file inputs")));
    }

    @PostMapping
    public ApiResponse<ApplyPortalConfig> create(@RequestBody ApplyPortalConfig portal,
                                                 @AuthenticationPrincipal UserDetails principal) {
        if (portal.getCode() == null || portal.getCode().isBlank()) {
            throw AppException.badRequest("A unique code is required");
        }
        portal.setId(null);
        ApplyPortalConfig saved = repository.save(portal);
        auditService.record(principal.getUsername(), AuditAction.PORTAL_CONFIG_CREATED, "portal",
                saved.getCode(), saved.getName());
        return ApiResponse.ok(saved, "Portal configuration created");
    }

    @PutMapping("/{id}")
    public ApiResponse<ApplyPortalConfig> update(@PathVariable String id,
                                                 @RequestBody ApplyPortalConfig incoming,
                                                 @AuthenticationPrincipal UserDetails principal) {
        ApplyPortalConfig existing = repository.findById(id)
                .orElseThrow(() -> AppException.notFound("Portal config"));

        existing.setName(incoming.getName());
        existing.setUrlPattern(incoming.getUrlPattern());
        existing.setEnabled(incoming.getEnabled());
        existing.setPriority(incoming.getPriority());
        existing.setReadySelector(incoming.getReadySelector());
        existing.setOpenFormSelector(incoming.getOpenFormSelector());
        existing.setFieldMappingJson(incoming.getFieldMappingJson());
        existing.setSubmitSelector(incoming.getSubmitSelector());
        existing.setSuccessSelector(incoming.getSuccessSelector());
        existing.setFailureText(incoming.getFailureText());
        existing.setDismissSelectors(incoming.getDismissSelectors());
        existing.setDryRun(incoming.getDryRun());
        existing.setMaxWaitSeconds(incoming.getMaxWaitSeconds());

        ApplyPortalConfig saved = repository.save(existing);
        auditService.record(principal.getUsername(), AuditAction.PORTAL_CONFIG_UPDATED, "portal",
                saved.getCode(), null);
        return ApiResponse.ok(saved, "Portal configuration updated");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, @AuthenticationPrincipal UserDetails principal) {
        ApplyPortalConfig portal = repository.findById(id)
                .orElseThrow(() -> AppException.notFound("Portal config"));
        repository.delete(portal);
        auditService.record(principal.getUsername(), AuditAction.PORTAL_CONFIG_DELETED, "portal",
                portal.getCode(), null);
        return ApiResponse.message("Portal configuration deleted");
    }
}
