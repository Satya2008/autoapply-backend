package com.naukriradar.core.controller;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.dto.response.SessionUserResponse;
import com.naukriradar.core.service.AuthService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * For the gateway: whose session a token is. The token goes in the body, never the URL, so it
 * doesn't end up in access logs. Not routed by the gateway.
 */
@RestController
@Validated
@RequestMapping("/internal/v1/sessions")
public class InternalSessionController {

	private final AuthService auth;

	public InternalSessionController(AuthService auth) {
		this.auth = auth;
	}

	public record ResolveRequest(@NotBlank @Size(max = 200) String token) {
	}

	@PostMapping("/resolve")
	public SessionUserResponse resolve(@RequestBody ResolveRequest request) {
		return auth.resolve(request.token()).orElseThrow(() -> new NotFoundException("No such session."));
	}

}
