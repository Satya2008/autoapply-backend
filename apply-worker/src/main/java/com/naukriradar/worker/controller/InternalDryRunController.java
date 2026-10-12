package com.naukriradar.worker.controller;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.naukriradar.worker.browser.BrowserApplyEngine;
import com.naukriradar.worker.browser.BrowserApplyEngine.FormResult;
import com.naukriradar.worker.client.ResumeFiles;
import com.naukriradar.worker.dto.request.DryRunRequest;
import com.naukriradar.worker.dto.response.DryRunResponse;
import com.naukriradar.worker.service.ScreenshotStore;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * For core-api's "dry run" of a portal: open the form, fill it with sample answers, report
 * which fields were found, and press nothing. Not routed by the gateway.
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalDryRunController {

	/** Clearly fake answers, for fields the caller didn't give one for. */
	static final Map<String, String> SAMPLE = Map.of("fullName", "Test Candidate", "firstName", "Test", "lastName", "Candidate",
			"email", "test@example.com", "phone", "+91 90000 00000", "phoneNational", "9000000000", "country", "India",
			"location", "Pune", "currentTitle", "Backend Developer");

	private final BrowserApplyEngine engine;
	private final ScreenshotStore screenshots;
	private final ResumeFiles resumeFiles;

	public InternalDryRunController(BrowserApplyEngine engine, ScreenshotStore screenshots, ResumeFiles resumeFiles) {
		this.engine = engine;
		this.screenshots = screenshots;
		this.resumeFiles = resumeFiles;
	}

	@PostMapping("/dry-run")
	public DryRunResponse dryRun(@Valid @RequestBody DryRunRequest request) {
		Map<String, String> answers = new HashMap<>(SAMPLE);
		if (request.answers() != null) {
			answers.putAll(request.answers());
		}
		Path resume = null;
		try {
			if (request.selectors().containsKey(BrowserApplyEngine.RESUME)) {
				// a clearly fake PDF: a dry run never uploads anyone's real resume
				resume = resumeFiles.sample();
				answers.put(BrowserApplyEngine.RESUME, resume.toString());
			}
			request.selectors().keySet().forEach(field -> answers.putIfAbsent(field, "sample"));
			FormResult result = engine.apply(request.url(), request.selectors(), answers, false);
			String screenshot = screenshots.save("dry-run-" + UUID.randomUUID(), 0, result.screenshot());
			return new DryRunResponse(result.filled(), result.missing(), result.note(), screenshot);
		}
		finally {
			resumeFiles.remove(resume);
		}
	}

}
