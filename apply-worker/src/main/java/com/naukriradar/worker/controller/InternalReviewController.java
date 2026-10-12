package com.naukriradar.worker.controller;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.naukriradar.worker.browser.BrowserApplyEngine;
import com.naukriradar.worker.browser.FormReviewer;
import com.naukriradar.worker.browser.FormReviewer.Question;
import com.naukriradar.worker.browser.FormReviewer.ReviewResult;
import com.naukriradar.worker.client.AnswersClient;
import com.naukriradar.worker.client.ResumeFiles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * For core-api's "Fill & Review": open the form in a visible Chrome on this machine, fill it
 * with the user's answers (AI for the rest), and leave it open for them to submit. Not routed
 * by the gateway.
 */
@RestController
@RequestMapping("/internal/v1")
public class InternalReviewController {

	private static final Logger log = LoggerFactory.getLogger(InternalReviewController.class);

	private final FormReviewer reviewer;
	private final ResumeFiles resumeFiles;
	private final AnswersClient answersClient;

	public InternalReviewController(FormReviewer reviewer, ResumeFiles resumeFiles, AnswersClient answersClient) {
		this.reviewer = reviewer;
		this.resumeFiles = resumeFiles;
		this.answersClient = answersClient;
	}

	/**
	 * @param selectors the portal's mapping, when the site has one
	 * @param answers the user's prepared answers, by field name
	 */
	public record ReviewRequest(@NotBlank String applicationId, @NotBlank String userId, @NotBlank @URL String url,
			Map<String, String> selectors, Map<String, String> answers) {
	}

	@PostMapping("/review")
	public ReviewResult review(@Valid @RequestBody ReviewRequest request) {
		Map<String, String> answers = new HashMap<>(request.answers() == null ? Map.of() : request.answers());
		try {
			// the resume for this job (tailored if the user made one), for the form's file box; the window stays
			// open, so the file does too
			Path resume = resumeFiles.download(request.userId(), request.applicationId());
			answers.put(BrowserApplyEngine.RESUME, resume.toString());
		}
		catch (RuntimeException ex) {
			log.info("Fill & Review for {} without a resume: {}", request.applicationId(), ex.getMessage());
			answers.remove(BrowserApplyEngine.RESUME);
		}
		return reviewer.review(request.url(), request.selectors(), answers, (List<Question> questions) -> {
			List<String> texts = questions.stream().map(Question::asText).toList();
			return answersClient.answer(request.userId(), request.applicationId(), texts);
		});
	}

}
