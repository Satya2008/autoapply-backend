package com.autoapply.matching.controller;

import com.autoapply.common.ApiResponse;
import com.autoapply.matching.entity.JobMatch;
import com.autoapply.matching.service.MatchingService;
import com.autoapply.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/matches")
@RequiredArgsConstructor
@Tag(name = "Matches")
public class MatchingController {

    private final MatchingService matchingService;
    private final UserService userService;

    @PostMapping("/run")
    @Operation(summary = "Score the current job pool against the signed-in user's profile")
    public ApiResponse<MatchingService.MatchSummary> run(@AuthenticationPrincipal UserDetails principal) {
        String userId = userService.getByEmail(principal.getUsername()).getId();
        return ApiResponse.ok(matchingService.matchForUser(userId), "Matching finished");
    }

    @GetMapping
    @Operation(summary = "Every scored match, joined with its job")
    public ApiResponse<List<MatchingService.MatchView>> matches(
            @AuthenticationPrincipal UserDetails principal) {
        String userId = userService.getByEmail(principal.getUsername()).getId();
        return ApiResponse.ok(matchingService.getMatchViews(userId, false));
    }

    @GetMapping("/recommended")
    public ApiResponse<List<MatchingService.MatchView>> recommended(
            @AuthenticationPrincipal UserDetails principal) {
        String userId = userService.getByEmail(principal.getUsername()).getId();
        return ApiResponse.ok(matchingService.getMatchViews(userId, true));
    }

    @GetMapping("/raw")
    public ApiResponse<List<JobMatch>> raw(@AuthenticationPrincipal UserDetails principal) {
        String userId = userService.getByEmail(principal.getUsername()).getId();
        return ApiResponse.ok(matchingService.getMatches(userId));
    }
}
