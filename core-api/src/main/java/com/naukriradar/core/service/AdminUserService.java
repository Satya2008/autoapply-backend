package com.naukriradar.core.service;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.client.MatchingClient;
import com.naukriradar.core.dto.response.AdminUserDetailResponse;
import com.naukriradar.core.dto.response.AdminUserResponse;
import com.naukriradar.core.dto.response.ApplicationStatsResponse;
import com.naukriradar.core.dto.response.TemporaryPasswordResponse;
import com.naukriradar.core.model.ApplicationStatus;
import com.naukriradar.core.model.Profile;
import com.naukriradar.core.model.Resume;
import com.naukriradar.core.model.User;
import com.naukriradar.core.model.UserRole;
import com.naukriradar.core.model.UserStatus;
import com.naukriradar.core.repository.ApplicationRepository;
import com.naukriradar.core.repository.ProfileRepository;
import com.naukriradar.core.repository.ResumeRepository;
import com.naukriradar.core.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The admin's view of every user: who they are, how far they got, and the few things an admin
 * may change (enable, role, a temporary password). An admin can't lock themselves out.
 */
@Service
public class AdminUserService {

	private final UserRepository users;
	private final ProfileRepository profiles;
	private final ResumeRepository resumes;
	private final ApplicationRepository applications;
	private final ApplicationService applicationService;
	private final ProfileService profileService;
	private final MatchingClient matching;
	private final AuthService auth;

	public AdminUserService(UserRepository users, ProfileRepository profiles, ResumeRepository resumes,
			ApplicationRepository applications, ApplicationService applicationService, ProfileService profileService,
			MatchingClient matching, AuthService auth) {
		this.users = users;
		this.profiles = profiles;
		this.resumes = resumes;
		this.applications = applications;
		this.applicationService = applicationService;
		this.profileService = profileService;
		this.matching = matching;
		this.auth = auth;
	}

	/** Every user, the most recently active first. */
	@Transactional(readOnly = true)
	public List<AdminUserResponse> list() {
		return users.findAll().stream()
				.map(this::summary)
				.sorted(Comparator.comparing(AdminUserResponse::lastLoginAt, Comparator.nullsLast(Comparator.reverseOrder()))
						.thenComparing(AdminUserResponse::createdAt, Comparator.reverseOrder()))
				.toList();
	}

	@Transactional(readOnly = true)
	public AdminUserDetailResponse get(String userId) {
		User user = load(userId);
		ApplicationStatsResponse stats = applicationService.stats(userId);
		int matches;
		try {
			matches = matching.matches(userId, 500).size();
		}
		catch (RuntimeException ex) {
			// matching-service down: the rest of the page still helps
			matches = -1;
		}
		return new AdminUserDetailResponse(summary(user), profileService.getProfile(userId), profileService.getSkills(userId),
				resumes.findByUserId(userId).map(Resume::getFileName).orElse(null), stats,
				applicationService.list(userId, null, null, 20).items(), matches);
	}

	/** Disabling signs the user out everywhere at once. */
	@Transactional
	public AdminUserResponse status(String adminId, String userId, UserStatus status) {
		guardSelf(adminId, userId, "disable");
		User user = load(userId);
		user.status(status);
		if (status == UserStatus.DISABLED) {
			auth.logoutEverywhere(userId);
		}
		return summary(user);
	}

	@Transactional
	public AdminUserResponse role(String adminId, String userId, UserRole role) {
		if (role != UserRole.ADMIN) {
			guardSelf(adminId, userId, "remove admin from");
		}
		User user = load(userId);
		user.role(role);
		return summary(user);
	}

	/** A one-time password for a user who forgot theirs; their sessions end, and they must change it. */
	@Transactional
	public TemporaryPasswordResponse resetPassword(String adminId, String userId) {
		guardSelf(adminId, userId, "reset the password of");
		User user = load(userId);
		String temporary = auth.temporaryPassword();
		user.password(auth.encode(temporary), true);
		auth.logoutEverywhere(userId);
		return new TemporaryPasswordResponse(temporary);
	}

	private AdminUserResponse summary(User user) {
		Map<ApplicationStatus, Long> byStatus = new EnumMap<>(ApplicationStatus.class);
		applications.countByStatus(user.getId()).forEach(c -> byStatus.put(c.getStatus(), c.getTotal()));
		long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
		long applied = ApplicationStatus.SENT.stream().mapToLong(s -> byStatus.getOrDefault(s, 0L)).sum();
		Profile profile = profiles.findById(user.getId()).orElse(null);
		return new AdminUserResponse(user.getId(), user.getEmail(), profile == null ? null : profile.getFullName(), user.getRole(),
				user.getStatus(), user.getCreatedAt(), user.getLastLoginAt(), user.hasPassword(), resumes.existsByUserId(user.getId()),
				profile == null ? 0 : profile.getSkills().size(), total, byStatus.getOrDefault(ApplicationStatus.NEEDS_YOU, 0L),
				applied);
	}

	private User load(String userId) {
		return users.findById(userId).orElseThrow(() -> new NotFoundException("No user " + userId + "."));
	}

	private static void guardSelf(String adminId, String userId, String what) {
		if (adminId.equals(userId)) {
			throw new BadRequestException("You can't " + what + " your own account.");
		}
	}

}
