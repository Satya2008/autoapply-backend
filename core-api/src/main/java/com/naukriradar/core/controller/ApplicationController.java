package com.naukriradar.core.controller;

import java.net.URI;
import java.util.List;

import com.naukriradar.common.security.CurrentUserProvider;
import com.naukriradar.core.dto.request.ScreeningQuestionsRequest;
import com.naukriradar.core.dto.request.StatusUpdateRequest;
import com.naukriradar.core.dto.response.ApplicationDetailResponse;
import com.naukriradar.core.dto.response.ApplicationPageResponse;
import com.naukriradar.core.dto.response.ApplicationStatsResponse;
import com.naukriradar.core.dto.response.ApplyRunResponse;
import com.naukriradar.core.dto.response.CoverLetterResponse;
import com.naukriradar.core.dto.response.NeedsYouResponse;
import com.naukriradar.core.dto.response.ScreeningAnswersResponse;
import com.naukriradar.core.model.ApplicationStatus;
import com.naukriradar.core.service.ApplicationService;
import com.naukriradar.core.service.ApplyProgressStreams;
import com.naukriradar.core.service.ApplyRunService;
import com.naukriradar.core.service.RagCoverLetterService;
import com.naukriradar.core.service.ScreeningAnswerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/me/applications")
public class ApplicationController {

	private final CurrentUserProvider currentUser;
	private final ApplyRunService runService;
	private final ApplicationService applicationService;
	private final RagCoverLetterService coverLetters;
	private final ScreeningAnswerService screeningAnswers;
	private final ApplyProgressStreams streams;

	public ApplicationController(CurrentUserProvider currentUser, ApplyRunService runService,
			ApplicationService applicationService, RagCoverLetterService coverLetters, ScreeningAnswerService screeningAnswers,
			ApplyProgressStreams streams) {
		this.currentUser = currentUser;
		this.runService = runService;
		this.applicationService = applicationService;
		this.coverLetters = coverLetters;
		this.screeningAnswers = screeningAnswers;
		this.streams = streams;
	}

	/**
	 * A live stream (Server-Sent Events) of this user's apply runs finishing, so the screen
	 * updates without polling. Events: {@code connected}, {@code apply-run-completed}.
	 */
	@GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter stream() {
		return streams.open(currentUser.currentUserId());
	}

	/**
	 * Writes a cover letter for this application from the parts of your resume that fit the job,
	 * and says if it claims anything your resume doesn't back. The same letter comes back until
	 * {@code regenerate=true}. Without AI you get a plain draft from a template.
	 */
	@PostMapping("/{id}/cover-letter")
	public CoverLetterResponse coverLetter(@PathVariable String id, @RequestParam(defaultValue = "false") boolean regenerate) {
		return coverLetters.write(currentUser.currentUserId(), id, regenerate);
	}

	/** Answers the form's screening questions from your profile and resume; the ones it can't are marked for you. */
	@PostMapping("/{id}/screening-answers")
	public ScreeningAnswersResponse screeningAnswers(@PathVariable String id,
			@Valid @RequestBody ScreeningQuestionsRequest request) {
		return screeningAnswers.answer(currentUser.currentUserId(), id, request.questions());
	}

	/**
	 * Turns your matches into applications in the background: low-risk sites go to the apply
	 * engine (simulated for now), everything else waits for you with answers ready.
	 */
	@PostMapping("/runs")
	public ResponseEntity<ApplyRunResponse> startRun() {
		ApplyRunResponse run = runService.start(currentUser.currentUserId());
		return ResponseEntity.accepted().location(URI.create("/api/v1/me/applications/runs/" + run.id())).body(run);
	}

	@GetMapping("/runs/{runId}")
	public ApplyRunResponse run(@PathVariable String runId) {
		return runService.get(currentUser.currentUserId(), runId);
	}

	@GetMapping
	public ApplicationPageResponse list(
			@RequestParam(required = false) ApplicationStatus status,
			@RequestParam(required = false) @Size(max = 40) String cursor,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
		return applicationService.list(currentUser.currentUserId(), status, cursor, limit);
	}

	/** The ones you have to send yourself, best match first, with answers to copy. */
	@GetMapping("/needs-you")
	public List<NeedsYouResponse> needsYou() {
		return applicationService.needsYou(currentUser.currentUserId());
	}

	@GetMapping("/stats")
	public ApplicationStatsResponse stats() {
		return applicationService.stats(currentUser.currentUserId());
	}

	@GetMapping("/{id}")
	public ApplicationDetailResponse get(@PathVariable String id) {
		return applicationService.get(currentUser.currentUserId(), id);
	}

	/** You applied yourself. Safe to call again. */
	@PostMapping("/{id}/done")
	public ApplicationDetailResponse done(@PathVariable String id) {
		return applicationService.markDone(currentUser.currentUserId(), id);
	}

	/** Safe to call again. */
	@PostMapping("/{id}/skip")
	public ApplicationDetailResponse skip(@PathVariable String id) {
		return applicationService.skip(currentUser.currentUserId(), id);
	}

	@PatchMapping("/{id}/status")
	public ApplicationDetailResponse updateStatus(@PathVariable String id, @Valid @RequestBody StatusUpdateRequest request) {
		return applicationService.updateStatus(currentUser.currentUserId(), id, request);
	}

}
