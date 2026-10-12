package com.naukriradar.core.service;

import java.util.Locale;

import com.naukriradar.core.model.Profile;
import com.naukriradar.core.model.User;
import com.naukriradar.core.model.UserRole;
import com.naukriradar.core.repository.ProfileRepository;
import com.naukriradar.core.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Makes sure the configured admin exists (naukriradar.admin.email, with an initial password
 * from naukriradar.admin.initial-password, both kept in config/core-secrets.properties). The
 * initial password must be changed at the first sign-in. Nothing configured: nothing done.
 */
@Component
public class AdminBootstrap {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final UserRepository users;
	private final ProfileRepository profiles;
	private final AuthService auth;
	private final String email;
	private final String initialPassword;

	public AdminBootstrap(UserRepository users, ProfileRepository profiles, AuthService auth,
			@Value("${naukriradar.admin.email:}") String email,
			@Value("${naukriradar.admin.initial-password:}") String initialPassword) {
		this.users = users;
		this.profiles = profiles;
		this.auth = auth;
		this.email = email.strip().toLowerCase(Locale.ROOT);
		this.initialPassword = initialPassword;
	}

	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void ensureAdmin() {
		if (email.isEmpty()) {
			return;
		}
		User admin = users.findByEmail(email).orElseGet(() -> {
			User created = users.saveAndFlush(new User(email));
			Profile profile = new Profile(created);
			profile.setFullName("Admin");
			profiles.save(profile);
			log.info("Created the admin account {}", email);
			return created;
		});
		admin.role(UserRole.ADMIN);
		if (!admin.hasPassword() && initialPassword != null && initialPassword.length() >= 8) {
			admin.password(auth.encode(initialPassword), true);
		}
	}

}
