package com.naukriradar.core.controller;

import com.naukriradar.common.security.CurrentUserProvider;
import com.naukriradar.core.dto.request.ChangePasswordRequest;
import com.naukriradar.core.dto.request.LoginRequest;
import com.naukriradar.core.dto.request.RegisterRequest;
import com.naukriradar.core.dto.response.AuthResponse;
import com.naukriradar.core.dto.response.MeResponse;
import com.naukriradar.core.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-up and sign-in. Register and login are open; the rest need a session (the gateway sets
 * the caller from the "Authorization: Bearer" token).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService auth;
	private final CurrentUserProvider currentUser;

	public AuthController(AuthService auth, CurrentUserProvider currentUser) {
		this.auth = auth;
		this.currentUser = currentUser;
	}

	@PostMapping("/register")
	@ResponseStatus(HttpStatus.CREATED)
	public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
		return auth.register(request);
	}

	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) {
		return auth.login(request);
	}

	@PostMapping("/logout")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logout(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		if (authorization != null && authorization.startsWith("Bearer ")) {
			auth.logout(authorization.substring("Bearer ".length()).strip());
		}
	}

	/** Signs this account out on every device. */
	@PostMapping("/logout-everywhere")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void logoutEverywhere() {
		auth.logoutEverywhere(currentUser.currentUserId());
	}

	@GetMapping("/me")
	public MeResponse me() {
		return auth.me(currentUser.currentUserId());
	}

	@PostMapping("/change-password")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void changePassword(@Valid @RequestBody ChangePasswordRequest request,
			@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
		String currentToken = authorization != null && authorization.startsWith("Bearer ")
				? authorization.substring("Bearer ".length()).strip() : null;
		auth.changePassword(currentUser.currentUserId(), request, currentToken);
	}

}
