package com.autoapply.apply.controller;

import com.autoapply.analytics.AnalyticsService;
import com.autoapply.apply.entity.Application;
import com.autoapply.apply.service.AutoApplyService;
import com.autoapply.common.ApiResponse;
import com.autoapply.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/apply")
@RequiredArgsConstructor
@Tag(name = "Auto Apply")
public class ApplyController {

    private final AutoApplyService autoApplyService;
    private final AnalyticsService analyticsService;
    private final UserService userService;

    @PostMapping("/run")
    @Operation(summary = "Start an auto-apply run for the signed-in user")
    public ApiResponse<AutoApplyService.RunSummary> run(@AuthenticationPrincipal UserDetails principal) {
        String userId = userService.getByEmail(principal.getUsername()).getId();
        return ApiResponse.ok(autoApplyService.runForUser(userId), "Auto apply finished");
    }

    @GetMapping("/history")
    public ApiResponse<List<Application>> history(@AuthenticationPrincipal UserDetails principal) {
        String userId = userService.getByEmail(principal.getUsername()).getId();
        return ApiResponse.ok(autoApplyService.getUserApplications(userId));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@AuthenticationPrincipal UserDetails principal) {
        String userId = userService.getByEmail(principal.getUsername()).getId();
        return ApiResponse.ok(analyticsService.userStats(userId));
    }

    @GetMapping("/assisted")
    @Operation(summary = "Roles the engine will not submit, each with every answer prepared",
            description = "Sites that detect automation are never driven by the browser engine. "
                    + "They appear here instead, with the candidate's answers ready to paste so "
                    + "finishing one takes a click and a few pastes.")
    public ApiResponse<List<AutoApplyService.AssistedApplication>> assisted(
            @AuthenticationPrincipal UserDetails principal) {
        String userId = userService.getByEmail(principal.getUsername()).getId();
        return ApiResponse.ok(autoApplyService.getAssistedQueue(userId));
    }

    @PostMapping("/assisted/{id}/done")
    @Operation(summary = "Mark an assisted application as submitted")
    public ApiResponse<Application> markDone(@AuthenticationPrincipal UserDetails principal,
                                             @PathVariable String id) {
        String userId = userService.getByEmail(principal.getUsername()).getId();
        return ApiResponse.ok(autoApplyService.markApplied(userId, id), "Marked as applied");
    }

    @PostMapping("/assisted/{id}/skip")
    public ApiResponse<Application> skip(@AuthenticationPrincipal UserDetails principal,
                                         @PathVariable String id) {
        String userId = userService.getByEmail(principal.getUsername()).getId();
        return ApiResponse.ok(autoApplyService.skipAssisted(userId, id), "Skipped");
    }
}
