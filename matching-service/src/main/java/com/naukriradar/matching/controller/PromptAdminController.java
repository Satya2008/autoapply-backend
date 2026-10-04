package com.naukriradar.matching.controller;

import java.util.List;

import com.naukriradar.matching.dto.request.PromptVersionRequest;
import com.naukriradar.matching.dto.response.PromptResponse;
import com.naukriradar.matching.service.PromptService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/prompts")
public class PromptAdminController {

	private final PromptService prompts;

	public PromptAdminController(PromptService prompts) {
		this.prompts = prompts;
	}

	/** Every version of every prompt, newest version first. */
	@GetMapping
	public List<PromptResponse> list() {
		return prompts.list();
	}

	/** Adds a version; it stays inactive until activated (unless it is the first one). */
	@PostMapping("/{code}/versions")
	@ResponseStatus(HttpStatus.CREATED)
	public PromptResponse addVersion(@PathVariable String code, @Valid @RequestBody PromptVersionRequest request) {
		return prompts.createVersion(code, request);
	}

	/** Makes this version the one in use; activating an older one is the rollback. */
	@PostMapping("/{code}/versions/{version}/activate")
	public PromptResponse activate(@PathVariable String code, @PathVariable int version) {
		return prompts.activate(code, version);
	}

}
