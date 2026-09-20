package com.autoapply.user.service;

import com.autoapply.settings.SettingKeys;
import com.autoapply.settings.SettingsService;
import com.autoapply.user.entity.Role;
import com.autoapply.user.entity.User;
import com.autoapply.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;

/** Guarantees there is always a way into the admin dashboard on a fresh install. */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(2)
public class AdminBootstrap {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SettingsService settings;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void createAdminIfMissing() {
        if (userRepository.countByRole(Role.SUPER_ADMIN) > 0 || userRepository.countByRole(Role.ADMIN) > 0) {
            return;
        }

        String email = settings.getString(SettingKeys.ADMIN_BOOTSTRAP_EMAIL, "admin@autoapply.local")
                .trim().toLowerCase();
        String password = settings.getString(SettingKeys.ADMIN_BOOTSTRAP_PASSWORD, "Admin@12345");

        User existing = userRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            existing.setRole(Role.SUPER_ADMIN);
            userRepository.save(existing);
            log.info("Promoted existing account {} to SUPER_ADMIN", email);
            return;
        }

        userRepository.save(User.builder()
                .email(email)
                .password(passwordEncoder.encode(password))
                .fullName("Administrator")
                .role(Role.SUPER_ADMIN)
                .enabled(true)
                .autoApplyEnabled(false)
                .skills(new ArrayList<>())
                .targetRoles(new ArrayList<>())
                .build());

        log.warn("===============================================================");
        log.warn(" Administrator account created");
        log.warn("   email    : {}", email);
        log.warn("   password : {}", password);
        log.warn("   Change it from the admin dashboard after signing in.");
        log.warn("===============================================================");
    }
}
