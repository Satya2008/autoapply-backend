package com.naukriradar.core.controller;

import java.util.List;

import com.naukriradar.common.security.CurrentUserProvider;
import com.naukriradar.core.dto.response.AdminUserDetailResponse;
import com.naukriradar.core.dto.response.AdminUserResponse;
import com.naukriradar.core.dto.response.TemporaryPasswordResponse;
import com.naukriradar.core.model.UserRole;
import com.naukriradar.core.model.UserStatus;
import com.naukriradar.core.service.AdminUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The admin's user monitoring. The gateway lets only admins reach /api/v1/admin. */
@RestController
@RequestMapping("/api/v1/admin/users")
public class UserAdminController {

	private final AdminUserService admin;
	private final CurrentUserProvider currentUser;

	public UserAdminController(AdminUserService admin, CurrentUserProvider currentUser) {
		this.admin = admin;
		this.currentUser = currentUser;
	}

	public record StatusRequest(@NotNull UserStatus status) {
	}

	public record RoleRequest(@NotNull UserRole role) {
	}

	@GetMapping
	public List<AdminUserResponse> list() {
		return admin.list();
	}

	@GetMapping("/{id}")
	public AdminUserDetailResponse get(@PathVariable String id) {
		return admin.get(id);
	}

	@PatchMapping("/{id}/status")
	public AdminUserResponse status(@PathVariable String id, @Valid @RequestBody StatusRequest request) {
		return admin.status(currentUser.currentUserId(), id, request.status());
	}

	@PatchMapping("/{id}/role")
	public AdminUserResponse role(@PathVariable String id, @Valid @RequestBody RoleRequest request) {
		return admin.role(currentUser.currentUserId(), id, request.role());
	}

	/** A one-time password to pass on to a user who forgot theirs. */
	@PostMapping("/{id}/reset-password")
	public TemporaryPasswordResponse resetPassword(@PathVariable String id) {
		return admin.resetPassword(currentUser.currentUserId(), id);
	}

}
