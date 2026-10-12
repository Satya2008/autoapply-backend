package com.naukriradar.core.controller;

import java.net.URI;
import java.util.List;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.common.security.CurrentUserProvider;
import com.naukriradar.core.dto.request.ScreeningQuestionsRequest;
import com.naukriradar.core.dto.request.StatusUpdateRequest;
import com.naukriradar.core.dto.response.ApplicationDetailResponse;
import com.naukriradar.core.dto.response.ApplicationPageResponse;
import com.naukriradar.core.dto.response.ApplicationStatsResponse;
import com.naukriradar.core.dto.response.ApplyRunResponse;
import com.naukriradar.core.dto.response.CoverLetterResponse;
import com.naukriradar.core.dto.response.FillReviewResponse;
import com.naukriradar.core.dto.response.MatchFillResponse;
import com.naukriradar.core.dto.response.MatchOpenResponse;
import com.naukriradar.core.dto.response.NeedsYouResponse;
import com.naukriradar.core.dto.response.ScreeningAnswersResponse;
import com.naukriradar.core.dto.response.TailoredResumeResponse;
import com.naukriradar.core.model.ApplicationStatus;
import com.naukriradar.core.service.ApplicationService;
import com.naukriradar.core.service.ApplyProgressStreams;
import com.naukriradar.core.service.ApplyRunService;
import com.naukriradar.core.service.FillReviewService;
import com.naukriradar.core.service.RagCoverLetterService;
import com.naukriradar.core.service.ScreeningAnswerService;
import com.naukriradar.core.service.TailoredResumeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
	private final FillReviewService fillReview;
	private final TailoredResumeService tailoredResumes;

	public ApplicationController(CurrentUserProvider currentUser, ApplyRunService runService,
			ApplicationService applicationService, RagCoverLetterService coverLetters, ScreeningAnswerService screeningAnswers,
			ApplyProgressStreams streams, FillReviewService fillReview, TailoredResumeService tailoredResumes) {
		this.currentUser = currentUser;
		this.runService = runService;
		this.applicationService = applicationService;
		this.coverLetters = coverLetters;
		this.screeningAnswers = screeningAnswers;
		this.streams = streams;
		this.fillReview = fillReview;
		this.tailoredResumes = tailoredResumes;
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
	 * Opens the job's form in Chrome on this computer and fills it for you; AI answers the
	 * questions your profile doesn't cover. Nothing is submitted: you check it and press Submit.
	 */
	@PostMapping("/{id}/fill-review")
	public FillReviewResponse fillReview(@PathVariable String id) {
		return fillReview.fill(currentUser.currentUserId(), id);
	}

	/**
	 * Your resume rewritten by AI for this job: same facts, the job's most relevant first, never
	 * a skill or number your resume doesn't have. Kept, and uploaded when this form is filled.
	 */
	@PostMapping("/{id}/tailored-resume")
	public TailoredResumeResponse tailorResume(@PathVariable String id) {
		return tailoredResumes.tailor(currentUser.currentUserId(), id);
	}

	@GetMapping("/{id}/tailored-resume")
	public TailoredResumeResponse tailoredResume(@PathVariable String id) {
		return tailoredResumes.get(currentUser.currentUserId(), id);
	}

	/** The tailored resume as an ATS-friendly PDF. */
	@GetMapping(path = "/{id}/tailored-resume/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
	public ResponseEntity<byte[]> tailoredResumePdf(@PathVariable String id) {
		String userId = currentUser.currentUserId();
		byte[] pdf = tailoredResumes.pdf(userId, id)
				.orElseThrow(() -> new NotFoundException("No tailored resume for this application yet."));
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
						.filename(tailoredResumes.fileName(userId, id)).build().toString())
				.body(pdf);
	}

	/**
	 * "Apply" on a match: tracks it under Applications (waiting for you) and gives the link to
	 * apply on. Press "I applied" once you have.
	 */
	@PostMapping("/from-match/{jobId}/open")
	public MatchOpenResponse openMatch(@PathVariable @Size(max = 36) String jobId) {
		return fillReview.openMatch(currentUser.currentUserId(), jobId);
	}

	/** The same, straight from a match: the application is made (waiting for you) if there isn't one. */
	@PostMapping("/from-match/{jobId}/fill-review")
	public MatchFillResponse fillMatch(@PathVariable @Size(max = 36) String jobId) {
		return fillReview.fillMatch(currentUser.currentUserId(), jobId);
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
