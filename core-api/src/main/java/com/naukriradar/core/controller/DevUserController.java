package com.naukriradar.core.controller;

import com.naukriradar.core.dto.request.CreateUserRequest;
import com.naukriradar.core.dto.response.UserResponse;
import com.naukriradar.core.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Creates test users while there is no sign-up. Goes away with Phase 8. */
@RestController
@Profile({ "dev", "test" })
@RequestMapping("/api/v1/dev/users")
@RequiredArgsConstructor
public class DevUserController {

	private final UserService userService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
		return userService.createUser(request);
	}

}
