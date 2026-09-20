package com.autoapply.admin;

import com.autoapply.analytics.AnalyticsService;
import com.autoapply.audit.AuditAction;
import com.autoapply.audit.AuditService;
import com.autoapply.common.ApiResponse;
import com.autoapply.common.AppException;
import com.autoapply.user.entity.Role;
import com.autoapply.user.entity.User;
import com.autoapply.user.repository.RefreshTokenRepository;
import com.autoapply.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Admin · Users")
public class AdminUserController {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AnalyticsService analyticsService;
    private final AuditService auditService;

    @GetMapping
    public ApiResponse<Page<User>> list(@RequestParam(required = false) String q,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        PageRequest pageable = PageRequest.of(page, Math.min(100, size), Sort.by("createdAt").descending());
        Page<User> users = (q == null || q.isBlank())
                ? userRepository.findAll(pageable)
                : userRepository.search(q, pageable);
        return ApiResponse.ok(users);
    }

    @GetMapping("/{id}")
    public ApiResponse<User> get(@PathVariable String id) {
        return ApiResponse.ok(userRepository.findById(id).orElseThrow(() -> AppException.notFound("User")));
    }

    @GetMapping("/{id}/stats")
    public ApiResponse<Map<String, Object>> stats(@PathVariable String id) {
        return ApiResponse.ok(analyticsService.userStats(id));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Update a user's role, status or limits")
    public ApiResponse<User> update(@PathVariable String id,
                                    @RequestBody AdminUserUpdate request,
                                    @AuthenticationPrincipal UserDetails principal) {
        User user = userRepository.findById(id).orElseThrow(() -> AppException.notFound("User"));

        if (request.getRole() != null) {
            if (user.getRole() == Role.SUPER_ADMIN && !principal.getAuthorities().toString().contains("SUPER_ADMIN")) {
                throw AppException.forbidden("Only a super administrator can change another super administrator");
            }
            user.setRole(request.getRole());
            auditService.record(principal.getUsername(), AuditAction.USER_ROLE_CHANGED, "user", id,
                    "role=" + request.getRole());
        }
        if (request.getEnabled() != null) {
            user.setEnabled(request.getEnabled());
            if (!request.getEnabled()) refreshTokenRepository.revokeAllForUser(id);
            auditService.record(principal.getUsername(),
                    request.getEnabled() ? AuditAction.USER_ENABLED : AuditAction.USER_DISABLED, "user", id, null);
        }
        if (request.getAutoApplyEnabled() != null) user.setAutoApplyEnabled(request.getAutoApplyEnabled());
        if (request.getDailyApplyLimit() != null) user.setDailyApplyLimit(request.getDailyApplyLimit());
        if (request.getMinMatchScore() != null) user.setMinMatchScore(request.getMinMatchScore());
        if (request.getFullName() != null) user.setFullName(request.getFullName());

        return ApiResponse.ok(userRepository.save(user), "User updated");
    }

    @PostMapping("/{id}/unlock")
    public ApiResponse<Void> unlock(@PathVariable String id, @AuthenticationPrincipal UserDetails principal) {
        User user = userRepository.findById(id).orElseThrow(() -> AppException.notFound("User"));
        user.setLockedUntil(null);
        user.setFailedLoginAttempts(0);
        userRepository.save(user);
        auditService.record(principal.getUsername(), AuditAction.USER_UNLOCKED, "user", id, null);
        return ApiResponse.message("Account unlocked");
    }

    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> resetPassword(@PathVariable String id,
                                           @RequestBody ResetPasswordRequest request,
                                           @AuthenticationPrincipal UserDetails principal) {
        User user = userRepository.findById(id).orElseThrow(() -> AppException.notFound("User"));
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(id);
        auditService.record(principal.getUsername(), AuditAction.PASSWORD_CHANGED, "user", id, "reset by admin");
        return ApiResponse.message("Password reset. The user has been signed out everywhere.");
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<Void> delete(@PathVariable String id, @AuthenticationPrincipal UserDetails principal) {
        User user = userRepository.findById(id).orElseThrow(() -> AppException.notFound("User"));
        if (user.getEmail().equalsIgnoreCase(principal.getUsername())) {
            throw AppException.badRequest("You cannot delete your own account");
        }
        refreshTokenRepository.revokeAllForUser(id);
        userRepository.delete(user);
        auditService.record(principal.getUsername(), AuditAction.USER_DELETED, "user", id, user.getEmail());
        return ApiResponse.message("User deleted");
    }

    @Data
    public static class AdminUserUpdate {
        private Role role;
        private Boolean enabled;
        private Boolean autoApplyEnabled;
        private Integer dailyApplyLimit;
        private Integer minMatchScore;
        private String fullName;
    }

    @Data
    public static class ResetPasswordRequest {
        private String newPassword;
    }
}
