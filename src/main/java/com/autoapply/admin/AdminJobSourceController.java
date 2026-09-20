package com.autoapply.admin;

import com.autoapply.audit.AuditAction;
import com.autoapply.audit.AuditService;
import com.autoapply.common.ApiResponse;
import com.autoapply.common.AppException;
import com.autoapply.jobs.service.JobFetchService;
import com.autoapply.jobs.source.JobSourceConfig;
import com.autoapply.jobs.source.JobSourceConfigRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/job-sources")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Admin · Job Sources",
        description = "Connect any JSON job API by describing it here - no code change needed.")
public class AdminJobSourceController {

    private final JobSourceConfigRepository repository;
    private final JobFetchService jobFetchService;
    private final AuditService auditService;

    @GetMapping
    public ApiResponse<List<JobSourceConfig>> list() {
        return ApiResponse.ok(repository.findAllByOrderByPriorityAscNameAsc());
    }

    @GetMapping("/{id}")
    public ApiResponse<JobSourceConfig> get(@PathVariable String id) {
        return ApiResponse.ok(repository.findById(id).orElseThrow(() -> AppException.notFound("Job source")));
    }

    @PostMapping
    @Operation(summary = "Add a new job board")
    public ApiResponse<JobSourceConfig> create(@RequestBody JobSourceConfig source,
                                               @AuthenticationPrincipal UserDetails principal) {
        if (source.getCode() == null || source.getCode().isBlank()) {
            throw AppException.badRequest("A unique code is required");
        }
        if (repository.existsByCode(source.getCode())) {
            throw AppException.conflict("A job source with code '" + source.getCode() + "' already exists");
        }
        source.setId(null);
        JobSourceConfig saved = repository.save(source);
        auditService.record(principal.getUsername(), AuditAction.JOB_SOURCE_CREATED, "job_source",
                saved.getCode(), saved.getName());
        return ApiResponse.ok(saved, "Job source created");
    }

    @PutMapping("/{id}")
    public ApiResponse<JobSourceConfig> update(@PathVariable String id,
                                               @RequestBody JobSourceConfig incoming,
                                               @AuthenticationPrincipal UserDetails principal) {
        JobSourceConfig existing = repository.findById(id)
                .orElseThrow(() -> AppException.notFound("Job source"));

        existing.setName(incoming.getName());
        existing.setProviderType(incoming.getProviderType());
        existing.setEnabled(incoming.getEnabled());
        existing.setBaseUrl(incoming.getBaseUrl());
        existing.setSearchPath(incoming.getSearchPath());
        existing.setHttpMethod(incoming.getHttpMethod());
        existing.setHeadersJson(incoming.getHeadersJson());
        existing.setQueryParamsJson(incoming.getQueryParamsJson());
        existing.setBodyTemplate(incoming.getBodyTemplate());
        existing.setResultsPath(incoming.getResultsPath());
        existing.setFieldMappingJson(incoming.getFieldMappingJson());
        existing.setDefaultQueries(incoming.getDefaultQueries());
        existing.setPriority(incoming.getPriority());
        existing.setRateLimitPerHour(incoming.getRateLimitPerHour());
        existing.setTimeoutSeconds(incoming.getTimeoutSeconds());

        JobSourceConfig saved = repository.save(existing);
        auditService.record(principal.getUsername(), AuditAction.JOB_SOURCE_UPDATED, "job_source",
                saved.getCode(), null);
        return ApiResponse.ok(saved, "Job source updated");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id, @AuthenticationPrincipal UserDetails principal) {
        JobSourceConfig source = repository.findById(id).orElseThrow(() -> AppException.notFound("Job source"));
        repository.delete(source);
        auditService.record(principal.getUsername(), AuditAction.JOB_SOURCE_DELETED, "job_source",
                source.getCode(), null);
        return ApiResponse.message("Job source deleted");
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Run this source once and report what came back")
    public ApiResponse<JobFetchService.FetchSummary> test(@PathVariable String id,
                                                          @RequestParam(required = false) String query,
                                                          @RequestParam(defaultValue = "IN") String country,
                                                          @AuthenticationPrincipal UserDetails principal) {
        JobSourceConfig source = repository.findById(id).orElseThrow(() -> AppException.notFound("Job source"));
        String effectiveQuery = (query == null || query.isBlank())
                ? (source.getDefaultQueries() == null ? "developer" : source.getDefaultQueries().split(",")[0].trim())
                : query;

        auditService.record(principal.getUsername(), AuditAction.JOB_SOURCE_TESTED, "job_source",
                source.getCode(), effectiveQuery);
        return ApiResponse.ok(jobFetchService.fetchOne(source, effectiveQuery, country, 1),
                "Test run finished");
    }

    @PostMapping("/fetch-all")
    @Operation(summary = "Run every enabled source now")
    public ApiResponse<JobFetchService.FetchSummary> fetchAll(@AuthenticationPrincipal UserDetails principal) {
        auditService.record(principal.getUsername(), AuditAction.JOBS_FETCHED, "job_source", "all", "manual trigger");
        return ApiResponse.ok(jobFetchService.fetchAll(), "Fetch finished");
    }
}
