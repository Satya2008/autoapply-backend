package com.naukriradar.core.controller;

import java.nio.charset.StandardCharsets;

import com.naukriradar.common.security.CurrentUserProvider;
import com.naukriradar.core.dto.request.ConfirmUploadRequest;
import com.naukriradar.core.dto.request.DirectUploadRequest;
import com.naukriradar.core.dto.response.DirectUploadResponse;
import com.naukriradar.core.dto.response.DownloadUrlResponse;
import com.naukriradar.core.dto.response.ResumeResponse;
import com.naukriradar.core.dto.response.ResumeUploadResponse;
import com.naukriradar.core.service.ResumeDirectUploadService;
import com.naukriradar.core.service.ResumeFile;
import com.naukriradar.core.service.ResumeParsingService;
import com.naukriradar.core.service.ResumeService;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.RequestBody;
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
	private final ResumeParsingService resumeParsing;
	private final ResumeDirectUploadService directUpload;

	/** Uploads or replaces the resume and returns the skills found in it. */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResumeUploadResponse upload(@RequestPart("file") MultipartFile file) {
		return resumeService.upload(currentUser.currentUserId(), file);
	}

	/**
	 * Step 1 of a direct upload: a short-lived link to PUT the file straight into storage.
	 * Needs S3 storage.
	 */
	@PostMapping("/upload-url")
	public DirectUploadResponse uploadUrl(@Valid @RequestBody DirectUploadRequest request) {
		return directUpload.uploadUrl(currentUser.currentUserId(), request);
	}

	/** Step 2: the file is uploaded; check it, read it and make it the resume. */
	@PostMapping("/confirm")
	public ResumeUploadResponse confirm(@Valid @RequestBody ConfirmUploadRequest request) {
		return directUpload.confirm(currentUser.currentUserId(), request);
	}

	/** A short-lived link to download the resume straight from storage. Needs S3 storage. */
	@GetMapping("/download-url")
	public DownloadUrlResponse downloadUrl() {
		return directUpload.downloadUrl(currentUser.currentUserId());
	}

	/** Reads the current resume with AI again (e.g. after the prompt improved). Runs in the background. */
	@PostMapping("/parse")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void parse() {
		resumeParsing.parseInBackground(currentUser.currentUserId());
	}

	@GetMapping
	public ResumeResponse get() {
		return resumeService.get(currentUser.currentUserId());
	}

	/**
	 * The resume file. With S3 storage this redirects to a short-lived storage link, so the
	 * bytes don't pass through this service; with local storage it streams the file.
	 */
	@GetMapping("/file")
	public ResponseEntity<InputStreamResource> download() {
		String userId = currentUser.currentUserId();
		if (directUpload.available()) {
			return ResponseEntity.status(HttpStatus.FOUND).location(directUpload.downloadUrl(userId).url()).build();
		}
		ResumeFile file = resumeService.open(userId);
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
