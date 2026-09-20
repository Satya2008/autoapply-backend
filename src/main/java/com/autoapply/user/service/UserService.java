package com.autoapply.user.service;

import com.autoapply.audit.AuditAction;
import com.autoapply.audit.AuditService;
import com.autoapply.common.AppException;
import com.autoapply.config.JwtService;
import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import com.autoapply.user.dto.*;
import com.autoapply.user.entity.RefreshToken;
import com.autoapply.user.entity.Role;
import com.autoapply.user.entity.User;
import com.autoapply.user.repository.RefreshTokenRepository;
import com.autoapply.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SettingsService settings;
    private final AuditService auditService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (!settings.getBoolean(SettingKeys.APP_REGISTRATION_ENABLED, true)) {
            throw AppException.forbidden("New registrations are currently disabled");
        }
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw AppException.conflict("That email is already registered");
        }
        validatePassword(request.getPassword());

        boolean firstUser = userRepository.count() == 0;
        boolean promoteFirst = settings.getBoolean(SettingKeys.ADMIN_FIRST_USER_IS_ADMIN, false);

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .location(request.getLocation())
                .currentRole(request.getCurrentRole())
                .experienceYears(request.getExperienceYears())
                .expectedSalary(request.getExpectedSalary())
                .preferredJobType(request.getPreferredJobType())
                .skills(request.getSkills() == null ? new ArrayList<>() : new ArrayList<>(request.getSkills()))
                .targetRoles(request.getTargetRoles() == null ? new ArrayList<>() : new ArrayList<>(request.getTargetRoles()))
                .role(firstUser && promoteFirst ? Role.SUPER_ADMIN : Role.USER)
                .build();

        user = userRepository.save(user);
        auditService.record(email, AuditAction.REGISTER, "user", user.getId(), null);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    auditService.recordFailure(email, AuditAction.LOGIN_FAILED, "user", email, "no such account");
                    return AppException.unauthorized("Invalid email or password");
                });

        if (user.isLocked()) {
            throw AppException.forbidden("Account is locked until " + user.getLockedUntil()
                    + " after too many failed attempts");
        }
        if (!Boolean.TRUE.equals(user.getEnabled())) {
            throw AppException.forbidden("This account has been disabled");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            registerFailedAttempt(user);
            auditService.recordFailure(email, AuditAction.LOGIN_FAILED, "user", user.getId(), "bad password");
            throw AppException.unauthorized("Invalid email or password");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        auditService.record(email, AuditAction.LOGIN_SUCCESS, "user", user.getId(), null);
        return buildAuthResponse(user);
    }

    private void registerFailedAttempt(User user) {
        int attempts = (user.getFailedLoginAttempts() == null ? 0 : user.getFailedLoginAttempts()) + 1;
        user.setFailedLoginAttempts(attempts);
        int max = settings.getInt(SettingKeys.SECURITY_MAX_LOGIN_ATTEMPTS, 8);
        if (attempts >= max) {
            int minutes = settings.getInt(SettingKeys.SECURITY_LOCKOUT_MINUTES, 15);
            user.setLockedUntil(LocalDateTime.now().plusMinutes(minutes));
            log.warn("Locked account {} for {} minutes after {} failed attempts", user.getEmail(), minutes, attempts);
        }
        userRepository.save(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        RefreshToken stored = refreshTokenRepository.findByToken(refreshTokenValue)
                .orElseThrow(() -> AppException.unauthorized("Refresh token is not recognised"));
        if (!stored.isUsable()) {
            throw AppException.unauthorized("Refresh token has expired or been revoked");
        }
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> AppException.unauthorized("Account no longer exists"));

        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        auditService.record(user.getEmail(), AuditAction.TOKEN_REFRESHED, "user", user.getId(), null);
        return buildAuthResponse(user);
    }

    @Transactional
    public void logout(String email) {
        userRepository.findByEmail(email).ifPresent(user -> {
            refreshTokenRepository.revokeAllForUser(user.getId());
            auditService.record(email, AuditAction.LOGOUT, "user", user.getId(), null);
        });
    }

    private AuthResponse buildAuthResponse(User user) {
        String token = jwtService.generateToken(user.getEmail(), user.getId(), user.getRole().name());
        RefreshToken refreshToken = refreshTokenRepository.save(RefreshToken.builder()
                .token(UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""))
                .userId(user.getId())
                .expiresAt(LocalDateTime.now().plusDays(settings.getLong(SettingKeys.SECURITY_REFRESH_EXPIRY_DAYS, 30)))
                .build());

        return AuthResponse.builder()
                .token(token)
                .refreshToken(refreshToken.getToken())
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .role(user.getRole().name())
                .expiresInSeconds(settings.getLong(SettingKeys.SECURITY_JWT_EXPIRY_MINUTES, 1440) * 60)
                .build();
    }

    private void validatePassword(String password) {
        int min = settings.getInt(SettingKeys.SECURITY_PASSWORD_MIN_LENGTH, 8);
        if (password == null || password.length() < min) {
            throw AppException.badRequest("Password must be at least " + min + " characters");
        }
        if (settings.getBoolean(SettingKeys.SECURITY_PASSWORD_REQUIRE_SPECIAL, false)
                && password.matches("^[a-zA-Z0-9]*$")) {
            throw AppException.badRequest("Password must contain at least one special character");
        }
    }

    // ------------------------------------------------------------- profile

    public User getByEmail(String email) {
        return userRepository.findByEmail(email).orElseThrow(() -> AppException.notFound("User"));
    }

    public User getById(String id) {
        return userRepository.findById(id).orElseThrow(() -> AppException.notFound("User"));
    }

    @Transactional
    public User updateProfile(String email, ProfileUpdateRequest request) {
        User user = getByEmail(email);
        if (request.getFullName() != null) user.setFullName(request.getFullName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getLocation() != null) user.setLocation(request.getLocation());
        if (request.getCurrentRole() != null) user.setCurrentRole(request.getCurrentRole());
        if (request.getExperienceYears() != null) user.setExperienceYears(request.getExperienceYears());
        if (request.getExpectedSalary() != null) user.setExpectedSalary(request.getExpectedSalary());
        if (request.getPreferredJobType() != null) user.setPreferredJobType(request.getPreferredJobType());
        if (request.getSkills() != null) user.setSkills(new ArrayList<>(request.getSkills()));
        if (request.getTargetRoles() != null) user.setTargetRoles(new ArrayList<>(request.getTargetRoles()));
        if (request.getPreferredLocations() != null) user.setPreferredLocations(new ArrayList<>(request.getPreferredLocations()));
        if (request.getExcludedCompanies() != null) user.setExcludedCompanies(new ArrayList<>(request.getExcludedCompanies()));
        if (request.getExcludedKeywords() != null) user.setExcludedKeywords(new ArrayList<>(request.getExcludedKeywords()));
        if (request.getCoverLetterTemplate() != null) user.setCoverLetterTemplate(request.getCoverLetterTemplate());
        if (request.getLinkedinUrl() != null) user.setLinkedinUrl(request.getLinkedinUrl());
        if (request.getGithubUrl() != null) user.setGithubUrl(request.getGithubUrl());
        if (request.getPortfolioUrl() != null) user.setPortfolioUrl(request.getPortfolioUrl());
        if (request.getNoticePeriod() != null) user.setNoticePeriod(request.getNoticePeriod());
        if (request.getWillingToRelocate() != null) user.setWillingToRelocate(request.getWillingToRelocate());
        if (request.getAutoApplyEnabled() != null) user.setAutoApplyEnabled(request.getAutoApplyEnabled());
        if (request.getDailyApplyLimit() != null) user.setDailyApplyLimit(request.getDailyApplyLimit());
        if (request.getMinMatchScore() != null) user.setMinMatchScore(request.getMinMatchScore());
        if (request.getNotificationsEnabled() != null) user.setNotificationsEnabled(request.getNotificationsEnabled());

        User saved = userRepository.save(user);
        auditService.record(email, AuditAction.PROFILE_UPDATED, "user", user.getId(), null);
        return saved;
    }

    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = getByEmail(email);
        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw AppException.badRequest("Current password is incorrect");
        }
        validatePassword(newPassword);
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user.getId());
        auditService.record(email, AuditAction.PASSWORD_CHANGED, "user", user.getId(), null);
    }

    public List<User> findAutoApplyCandidates() {
        return userRepository.findByAutoApplyEnabledTrueAndEnabledTrue();
    }
}
