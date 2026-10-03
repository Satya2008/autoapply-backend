package com.naukriradar.core.user;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Creates test users while there is no registration. Removed in Phase 8. */
@RestController
@Profile({ "dev", "test" })
@RequestMapping("/api/v1/dev/users")
@RequiredArgsConstructor
class DevUserController {

	private final UserService userService;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	UserResponse create(@Valid @RequestBody CreateUserRequest request) {
		return userService.createUser(request);
	}

}
