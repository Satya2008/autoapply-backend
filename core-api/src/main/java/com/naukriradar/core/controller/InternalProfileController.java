package com.naukriradar.core.controller;

import java.nio.charset.StandardCharsets;

import com.naukriradar.core.dto.response.MatchingProfileResponse;
import com.naukriradar.core.service.ProfileService;
import com.naukriradar.core.service.ResumeFile;
import com.naukriradar.core.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service API. The gateway has no route to /internal, so only other services on
 * the private network can call it.
 */
@RestController
@RequestMapping("/internal/v1/users")
@RequiredArgsConstructor
public class InternalProfileController {

	private final ProfileService profileService;
	private final ResumeService resumeService;

	@GetMapping("/{userId}/matching-profile")
	public MatchingProfileResponse matchingProfile(@PathVariable String userId) {
		return profileService.getMatchingProfile(userId);
	}

	/**
	 * The user's resume file, for the apply worker to attach to an application form. Always
	 * streamed (no storage redirect): the worker may not reach the storage directly.
	 */
	@GetMapping("/{userId}/resume/file")
	public ResponseEntity<InputStreamResource> resumeFile(@PathVariable String userId) {
		ResumeFile file = resumeService.open(userId);
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.contentType()))
				.contentLength(file.sizeBytes())
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
				.body(new InputStreamResource(file.content()));
	}

}
