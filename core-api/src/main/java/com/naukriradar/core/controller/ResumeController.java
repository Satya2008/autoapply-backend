package com.naukriradar.core.controller;

import java.nio.charset.StandardCharsets;

import com.naukriradar.common.security.CurrentUserProvider;
import com.naukriradar.core.dto.response.ResumeResponse;
import com.naukriradar.core.dto.response.ResumeUploadResponse;
import com.naukriradar.core.service.ResumeFile;
import com.naukriradar.core.service.ResumeService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/me/resume")
@RequiredArgsConstructor
public class ResumeController {

	private final CurrentUserProvider currentUser;
	private final ResumeService resumeService;

	/** Uploads or replaces the resume and returns the skills found in it. */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResumeUploadResponse upload(@RequestPart("file") MultipartFile file) {
		return resumeService.upload(currentUser.currentUserId(), file);
	}

	@GetMapping
	public ResumeResponse get() {
		return resumeService.get(currentUser.currentUserId());
	}

	@GetMapping("/file")
	public ResponseEntity<InputStreamResource> download() {
		ResumeFile file = resumeService.open(currentUser.currentUserId());
		ContentDisposition disposition = ContentDisposition.attachment()
				.filename(file.fileName(), StandardCharsets.UTF_8)
				.build();
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(file.contentType()))
				.contentLength(file.sizeBytes())
				.header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
				.header("X-Content-Type-Options", "nosniff")
				.body(new InputStreamResource(file.content()));
	}

	@DeleteMapping
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete() {
		resumeService.delete(currentUser.currentUserId());
	}

}
