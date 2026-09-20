package com.autoapply.user.controller;

import com.autoapply.common.ApiResponse;
import com.autoapply.user.dto.*;
import com.autoapply.user.entity.User;
import com.autoapply.user.service.ResumeService;
import com.autoapply.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth & Profile")
public class UserController {

    private final UserService userService;
    private final ResumeService resumeService;

    @PostMapping("/register")
    @Operation(summary = "Create an account")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(userService.register(request), "Account created");
    }

    @PostMapping("/login")
    @Operation(summary = "Exchange credentials for tokens")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(userService.login(request), "Signed in");
    }

    @PostMapping("/refresh")
    @Operation(summary = "Exchange a refresh token for a fresh access token")
    public ApiResponse<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.ok(userService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke every refresh token for the current user")
    public ApiResponse<Void> logout(@AuthenticationPrincipal UserDetails principal) {
        userService.logout(principal.getUsername());
        return ApiResponse.message("Signed out");
    }

    @GetMapping("/profile")
    public ApiResponse<User> profile(@AuthenticationPrincipal UserDetails principal) {
        return ApiResponse.ok(userService.getByEmail(principal.getUsername()));
    }

    @PutMapping("/profile")
    @Operation(summary = "Update the signed-in user's profile and preferences")
    public ApiResponse<User> updateProfile(@AuthenticationPrincipal UserDetails principal,
                                           @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.ok(userService.updateProfile(principal.getUsername(), request), "Profile updated");
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal UserDetails principal,
                                            @Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(principal.getUsername(), request.getCurrentPassword(), request.getNewPassword());
        return ApiResponse.message("Password changed. Please sign in again.");
    }

    @PostMapping(value = "/resume", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a resume; skills are extracted automatically")
    public ApiResponse<User> uploadResume(@AuthenticationPrincipal UserDetails principal,
                                          @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(resumeService.upload(principal.getUsername(), file), "Resume uploaded and parsed");
    }

    @GetMapping("/resume")
    public ResponseEntity<ByteArrayResource> downloadResume(@AuthenticationPrincipal UserDetails principal) {
        User user = userService.getByEmail(principal.getUsername());
        byte[] bytes = resumeService.download(principal.getUsername());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + (user.getResumeFileName() == null ? "resume" : user.getResumeFileName()) + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(new ByteArrayResource(bytes));
    }

    @Data
    public static class RefreshRequest {
        @NotBlank
        private String refreshToken;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank
        private String currentPassword;
        @NotBlank
        private String newPassword;
    }
}
