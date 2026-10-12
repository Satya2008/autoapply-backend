package com.naukriradar.core.controller;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.naukriradar.core.dto.response.ScreeningAnswersResponse;
import com.naukriradar.core.service.ResumeFile;
import com.naukriradar.core.service.ResumeService;
import com.naukriradar.core.service.ScreeningAnswerService;
import com.naukriradar.core.service.TailoredResumeService;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service: the apply worker asks for answers to the questions a form asks, and for
 * the resume to upload with an application. Not
 * routed by the gateway.
 */
@RestController
@Validated
@RequestMapping("/internal/v1/users/{userId}/applications/{applicationId}")
public class InternalApplicationController {

	/** Questions per AI call; a long form is answered in a few calls. */
	private static final int BATCH = 10;

	private final ScreeningAnswerService screeningAnswers;
	private final TailoredResumeService tailoredResumes;
	private final ResumeService resumes;

	public InternalApplicationController(ScreeningAnswerService screeningAnswers, TailoredResumeService tailoredResumes,
			ResumeService resumes) {
		this.screeningAnswers = screeningAnswers;
		this.tailoredResumes = tailoredResumes;
		this.resumes = resumes;
	}

	/**
	 * The resume to upload with this application: the one tailored to its job when the user made
	 * one, else their original. Always streamed, for the apply worker.
	 */
	@GetMapping("/resume/file")
	public ResponseEntity<InputStreamResource> resumeFile(@PathVariable String userId, @PathVariable String applicationId) {
		Optional<byte[]> tailored = tailoredResumes.pdf(userId, applicationId);
		if (tailored.isPresent()) {
			return file(tailoredResumes.fileName(userId, applicationId), MediaType.APPLICATION_PDF_VALUE, tailored.get().length,
					new ByteArrayInputStream(tailored.get()));
		}
		ResumeFile original = resumes.open(userId);
		return file(original.fileName(), original.contentType(), original.sizeBytes(), original.content());
	}

	private static ResponseEntity<InputStreamResource> file(String name, String type, long size, InputStream content) {
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(type))
				.contentLength(size)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
				.body(new InputStreamResource(content));
	}

	public record QuestionsRequest(@NotEmpty @Size(max = 60) List<String> questions) {
	}

	@PostMapping("/answers")
	public ScreeningAnswersResponse answers(@PathVariable String userId, @PathVariable String applicationId,
			@RequestBody QuestionsRequest request) {
		List<String> questions = request.questions().stream().filter(q -> q != null && !q.isBlank()).toList();
		List<ScreeningAnswersResponse.Answer> answers = new ArrayList<>();
		String answeredBy = null;
		String note = null;
		for (int from = 0; from < questions.size(); from += BATCH) {
			ScreeningAnswersResponse part = screeningAnswers.answer(userId, applicationId,
					questions.subList(from, Math.min(from + BATCH, questions.size())));
			answers.addAll(part.answers());
			answeredBy = answeredBy != null ? answeredBy : part.answeredBy();
			note = note != null ? note : part.note();
		}
		return new ScreeningAnswersResponse(applicationId, answers, answeredBy, note);
	}

}
