package com.naukriradar.core.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.UnauthenticatedException;
import com.naukriradar.core.dto.request.ChangePasswordRequest;
import com.naukriradar.core.dto.request.LoginRequest;
import com.naukriradar.core.dto.request.RegisterRequest;
import com.naukriradar.core.dto.response.AuthResponse;
import com.naukriradar.core.dto.response.MeResponse;
import com.naukriradar.core.dto.response.SessionUserResponse;
import com.naukriradar.core.model.Profile;
import com.naukriradar.core.model.Session;
import com.naukriradar.core.model.User;
import com.naukriradar.core.model.UserStatus;
import com.naukriradar.core.repository.ProfileRepository;
import com.naukriradar.core.repository.SessionRepository;
import com.naukriradar.core.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-up and sign-in against our own users table: BCrypt passwords, random session tokens of
 * which only the SHA-256 is stored. Five wrong passwords lock the account for 15 minutes; a
 * session lasts 30 days. The gateway turns a token into the caller's id for every request.
 */
@Service
public class AuthService {

	static final int MAX_FAILED_LOGINS = 5;

	static final Duration LOCK = Duration.ofMinutes(15);

	static final Duration SESSION = Duration.ofDays(30);

	private static final String WRONG = "Wrong email or password.";

	private final UserRepository users;
	private final ProfileRepository profiles;
	private final SessionRepository sessions;
	private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
	private final SecureRandom random = new SecureRandom();
	private final Clock clock = Clock.systemUTC();

	public AuthService(UserRepository users, ProfileRepository profiles, SessionRepository sessions) {
		this.users = users;
		this.profiles = profiles;
		this.sessions = sessions;
	}

	/**
	 * A new account, signed in. An account made before passwords existed (no password yet) is
	 * claimed by setting one: its data stays.
	 */
	@Transactional
	public AuthResponse register(RegisterRequest request) {
		String email = normalise(request.email());
		User user = users.findByEmail(email).orElse(null);
		if (user != null && user.hasPassword()) {
			throw new ConflictException("An account with this email already exists. Sign in instead.");
		}
		if (user == null) {
			user = users.saveAndFlush(new User(email));
			profiles.save(new Profile(user));
		}
		user.password(encoder.encode(request.password()), false);
		Profile profile = profiles.findById(user.getId()).orElseGet(() -> null);
		if (profile != null && (profile.getFullName() == null || profile.getFullName().isBlank())) {
			profile.setFullName(request.name().strip());
		}
		return signIn(user);
	}

	@Transactional(noRollbackFor = UnauthenticatedException.class)
	public AuthResponse login(LoginRequest request) {
		Instant now = clock.instant();
		User user = users.findByEmail(normalise(request.email()))
				.orElseThrow(() -> new UnauthenticatedException(WRONG));
		if (user.lockedAt(now)) {
			throw new UnauthenticatedException("Too many wrong passwords. Try again in 15 minutes.");
		}
		if (!user.hasPassword()) {
			throw new UnauthenticatedException("This account has no password yet. Use Sign up with the same email to set one.");
		}
		if (!encoder.matches(request.password(), user.getPasswordHash())) {
			user.failedLogin(MAX_FAILED_LOGINS, now.plus(LOCK));
			throw new UnauthenticatedException(WRONG);
		}
		if (user.getStatus() != UserStatus.ACTIVE) {
			throw new UnauthenticatedException("This account is disabled. Contact the admin.");
		}
		return signIn(user);
	}

	@Transactional
	public void logout(String token) {
		sessions.findByTokenHash(hash(token)).ifPresent(s -> s.revoke(clock.instant()));
	}

	/** Signs every browser of this user out. */
	@Transactional
	public void logoutEverywhere(String userId) {
		sessions.revokeAll(userId, clock.instant());
	}

	@Transactional
	public void changePassword(String userId, ChangePasswordRequest request) {
		User user = users.findById(userId).orElseThrow(() -> new UnauthenticatedException("Sign in again."));
		if (!user.hasPassword() || !encoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new BadRequestException("The current password is wrong.");
		}
		if (request.currentPassword().equals(request.newPassword())) {
			throw new BadRequestException("Choose a password different from the current one.");
		}
		user.password(encoder.encode(request.newPassword()), false);
	}

	@Transactional(readOnly = true)
	public MeResponse me(String userId) {
		User user = users.findById(userId).orElseThrow(() -> new UnauthenticatedException("Sign in again."));
		return new MeResponse(user.getId(), user.getEmail(), name(user), user.getRole(), user.isMustChangePassword());
	}

	/** For the gateway: the user behind a live token of an active account. */
	@Transactional(readOnly = true)
	public Optional<SessionUserResponse> resolve(String token) {
		Instant now = clock.instant();
		return sessions.findByTokenHash(hash(token))
				.filter(s -> s.activeAt(now))
				.flatMap(s -> users.findById(s.getUserId()))
				.filter(u -> u.getStatus() == UserStatus.ACTIVE)
				.map(u -> new SessionUserResponse(u.getId(), u.getRole()));
	}

	/** A random password for a reset or the first admin: 16 letters and digits. */
	public String temporaryPassword() {
		String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < 16; i++) {
			out.append(alphabet.charAt(random.nextInt(alphabet.length())));
		}
		return out.toString();
	}

	public String encode(String password) {
		return encoder.encode(password);
	}

	private AuthResponse signIn(User user) {
		Instant now = clock.instant();
		byte[] bytes = new byte[32];
		random.nextBytes(bytes);
		String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		sessions.save(new Session(hash(token), user.getId(), now, now.plus(SESSION)));
		user.loggedIn(now);
		return new AuthResponse(token, user.getId(), user.getEmail(), name(user), user.getRole(), user.isMustChangePassword());
	}

	private String name(User user) {
		return profiles.findById(user.getId()).map(Profile::getFullName).orElse(null);
	}

	static String hash(String token) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static String normalise(String email) {
		return email.strip().toLowerCase(Locale.ROOT);
	}

}
