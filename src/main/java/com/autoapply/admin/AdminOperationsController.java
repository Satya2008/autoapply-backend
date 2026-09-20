package com.autoapply.admin;

import com.autoapply.audit.AuditAction;
import com.autoapply.audit.AuditLog;
import com.autoapply.audit.AuditService;
import com.autoapply.common.ApiResponse;
import com.autoapply.common.AppException;
import com.autoapply.matching.ai.AiService;
import com.autoapply.matching.ai.PromptTemplate;
import com.autoapply.matching.ai.PromptTemplateRepository;
import com.autoapply.notification.NotificationService;
import com.autoapply.scheduler.DynamicSchedulerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Admin · Operations")
public class AdminOperationsController {

    private final DynamicSchedulerService schedulerService;
    private final PromptTemplateRepository promptRepository;
    private final NotificationService notificationService;
    private final AiService aiService;
    private final AuditService auditService;

    // ------------------------------------------------------------ scheduler

    @GetMapping("/scheduler")
    @Operation(summary = "Every scheduled task with its cron, next run and last result")
    public ApiResponse<List<Map<String, Object>>> scheduler() {
        return ApiResponse.ok(schedulerService.status());
    }

    @PostMapping("/scheduler/{taskId}/run")
    @Operation(summary = "Run a scheduled task immediately")
    public ApiResponse<Void> runTask(@PathVariable String taskId,
                                     @AuthenticationPrincipal UserDetails principal) {
        return ApiResponse.message(schedulerService.triggerNow(taskId, principal.getUsername()));
    }

    @PostMapping("/scheduler/reload")
    public ApiResponse<Void> reloadSchedules() {
        schedulerService.rescheduleAll();
        return ApiResponse.message("Schedules rebuilt from the current settings");
    }

    // -------------------------------------------------------------- prompts

    @GetMapping("/prompts")
    @Operation(summary = "AI prompt templates - editing one changes how the model reasons")
    public ApiResponse<List<PromptTemplate>> prompts() {
        return ApiResponse.ok(promptRepository.findAllByOrderByNameAsc());
    }

    @PutMapping("/prompts/{code}")
    public ApiResponse<PromptTemplate> updatePrompt(@PathVariable String code,
                                                    @RequestBody PromptTemplate incoming,
                                                    @AuthenticationPrincipal UserDetails principal) {
        PromptTemplate existing = promptRepository.findById(code)
                .orElseThrow(() -> AppException.notFound("Prompt template"));
        existing.setName(incoming.getName());
        existing.setDescription(incoming.getDescription());
        existing.setTemplate(incoming.getTemplate());
        existing.setVariables(incoming.getVariables());
        existing.setEnabled(incoming.getEnabled());
        existing.setUpdatedBy(principal.getUsername());

        PromptTemplate saved = promptRepository.save(existing);
        auditService.record(principal.getUsername(), AuditAction.PROMPT_UPDATED, "prompt", code, null);
        return ApiResponse.ok(saved, "Prompt updated");
    }

    @PostMapping("/prompts/{code}/preview")
    @Operation(summary = "Render a prompt with sample values without calling the model")
    public ApiResponse<String> previewPrompt(@PathVariable String code,
                                             @RequestBody Map<String, String> variables) {
        String rendered = aiService.render(code, variables);
        if (rendered == null) throw AppException.notFound("Prompt template");
        return ApiResponse.ok(rendered);
    }

    @PostMapping("/ai/test")
    @Operation(summary = "Send a prompt to the configured AI provider and return its raw reply")
    public ApiResponse<Map<String, Object>> testAi(@RequestBody AiTestRequest request) {
        String provider = aiService.activeProvider().map(p -> p.name()).orElse("none");
        String reply = aiService.activeProvider()
                .map(p -> {
                    try {
                        return p.complete(request.getPrompt() == null
                                ? "Reply with the single word: ok" : request.getPrompt());
                    } catch (Exception e) {
                        return "ERROR: " + e.getMessage();
                    }
                })
                .orElse("No AI provider is configured. Add an API key in Settings.");
        return ApiResponse.ok(Map.of("provider", provider, "response", reply));
    }

    // -------------------------------------------------------- notifications

    @GetMapping("/notifications/channels")
    public ApiResponse<List<Map<String, Object>>> channels() {
        return ApiResponse.ok(notificationService.channelStatus());
    }

    @PostMapping("/notifications/test")
    @Operation(summary = "Send a test message through one channel")
    public ApiResponse<String> testChannel(@RequestParam String channel,
                                           @RequestParam(required = false) String recipient,
                                           @AuthenticationPrincipal UserDetails principal) {
        String target = (recipient == null || recipient.isBlank()) ? principal.getUsername() : recipient;
        return ApiResponse.ok(notificationService.test(channel, target));
    }

    // ---------------------------------------------------------------- audit

    @GetMapping("/audit")
    public ApiResponse<Page<AuditLog>> audit(@RequestParam(required = false) String actor,
                                             @RequestParam(required = false) AuditAction action,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(auditService.search(actor, action, PageRequest.of(page, Math.min(200, size))));
    }

    @GetMapping("/audit/actions")
    public ApiResponse<AuditAction[]> auditActions() {
        return ApiResponse.ok(AuditAction.values());
    }

    @Data
    public static class AiTestRequest {
        private String prompt;
    }
}
