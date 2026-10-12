package com.naukriradar.core.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.client.ApplyWorkerClient;
import com.naukriradar.core.client.MatchForApply;
import com.naukriradar.core.client.MatchingClient;
import com.naukriradar.core.dto.response.FillReviewResponse;
import com.naukriradar.core.dto.response.MatchFillResponse;
import com.naukriradar.core.dto.response.MatchOpenResponse;
import com.naukriradar.core.dto.response.PortalConfigResponse;
import com.naukriradar.core.model.Application;
import com.naukriradar.core.model.ApplicationStatus;
import com.naukriradar.core.model.Profile;
import com.naukriradar.core.model.RiskBand;
import com.naukriradar.core.repository.ApplicationRepository;
import com.naukriradar.core.repository.ProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * "Fill & Review": the apply worker opens the job's form in a Chrome window on this machine,
 * fills everything it can (AI answers the rest), and stops before Submit. The application then
 * waits for the user with "only the last step left". Nothing is ever submitted here.
 */
@Service
public class FillReviewService {

	/** Not yet sent, and not in the worker's hands right now. */
	private static final Set<ApplicationStatus> FILLABLE = EnumSet.of(ApplicationStatus.PLANNED,
			ApplicationStatus.FAILED, ApplicationStatus.SIMULATED, ApplicationStatus.NEEDS_YOU);

	/** Prefix of the reason shown while the filled form waits in Chrome; the frontend looks for it. */
	public static final String READY = "Ready in Chrome:";

	private final ApplicationRepository applications;
	private final ProfileRepository profiles;
	private final PrefillService prefill;
	private final PortalConfigService portals;
	private final ApplyWorkerClient worker;
	private final ApplicationStateMachine stateMachine;
	private final TransactionTemplate transaction;
	private final MatchingClient matching;
	private final ApplyPlanner planner;

	public FillReviewService(ApplicationRepository applications, ProfileRepository profiles, PrefillService prefill,
			PortalConfigService portals, ApplyWorkerClient worker, ApplicationStateMachine stateMachine,
			PlatformTransactionManager transactionManager, MatchingClient matching, ApplyPlanner planner) {
		this.matching = matching;
		this.planner = planner;
		this.applications = applications;
		this.profiles = profiles;
		this.prefill = prefill;
		this.portals = portals;
		this.worker = worker;
		this.stateMachine = stateMachine;
		this.transaction = new TransactionTemplate(transactionManager);
	}

	/**
	 * "Fill for me" straight from a match: makes the application (waiting for the user) if there
	 * isn't one yet, then fills its form.
	 */
	public MatchFillResponse fillMatch(String userId, String jobId) {
		MatchForApply match = matching.matches(userId, MATCHES_LOOKED_AT).stream()
				.filter(m -> jobId.equals(m.jobId()))
				.findFirst()
				.orElseThrow(() -> new NotFoundException("No match for job " + jobId + "."));
		String applicationId = planner.planOne(userId, match).getId();
		return new MatchFillResponse(applicationId, fill(userId, applicationId));
	}

	/**
	 * "Apply" on a match: the application is made (waiting for the user, who applies on the site)
	 * if there isn't one, so it shows under Applications; returns the link to open.
	 */
	public MatchOpenResponse openMatch(String userId, String jobId) {
		MatchForApply match = matching.matches(userId, MATCHES_LOOKED_AT).stream()
				.filter(m -> jobId.equals(m.jobId()))
				.findFirst()
				.orElseThrow(() -> new NotFoundException("No match for job " + jobId + "."));
		return new MatchOpenResponse(planner.planOne(userId, match).getId(), match.applyUrl());
	}

	/** Matches are few hundred at most; the one asked for is among them. */
	private static final int MATCHES_LOOKED_AT = 500;

	public FillReviewResponse fill(String userId, String applicationId) {
		Job job = transaction.execute(status -> prepare(userId, applicationId));
		// outside any transaction: the browser takes a minute or two
		JsonNode reply = worker.review(applicationId, userId, job.url(), job.selectors(), job.answers());
		FillReviewResponse result = read(reply);
		if (result.opened()) {
			transaction.executeWithoutResult(status -> markReady(userId, applicationId, result));
		}
		return result;
	}

	private Job prepare(String userId, String applicationId) {
		Application application = load(userId, applicationId);
		if (!FILLABLE.contains(application.getStatus())) {
			throw new BadRequestException("This application is " + application.getStatus().name().toLowerCase()
					+ "; there's no form to fill.");
		}
		if (application.getRiskBand() == RiskBand.HIGH) {
			// these sites ban accounts that use automation; the user applies there themselves
			throw new BadRequestException("This site doesn't allow automated filling. Open it and use your answers below.");
		}
		String url = application.getApplyUrl();
		if (url == null || url.isBlank()) {
			throw new BadRequestException("This job has no application link.");
		}
		Profile profile = profiles.findById(userId)
				.orElseThrow(() -> new NotFoundException("Set up your profile first."));
		Map<String, String> answers = new LinkedHashMap<>(prefill.answers(profile));
		return new Job(url, selectorsFor(url), answers);
	}

	/**
	 * The mapping of the most specific enabled portal covering the URL's host; empty for an
	 * unknown site, whose form the worker reads by its labels. Looked up without
	 * {@code forHost}: its "not found" would mark the surrounding transaction rollback-only.
	 */
	private Map<String, String> selectorsFor(String url) {
		String host = RiskClassifier.host(url);
		if (host == null) {
			return Map.of();
		}
		return portals.list().stream()
				.filter(p -> p.enabled() && RiskClassifier.covers(p.domain(), host))
				.max(Comparator.comparingInt(p -> p.domain().length()))
				.map(PortalConfigResponse::selectors)
				.filter(Objects::nonNull)
				.orElse(Map.of());
	}

	private void markReady(String userId, String applicationId, FillReviewResponse result) {
		Application application = load(userId, applicationId);
		String reason = readyReason(result);
		if (application.getStatus() != ApplicationStatus.NEEDS_YOU) {
			stateMachine.move(application, ApplicationStatus.NEEDS_YOU, reason);
		}
		application.needsYouBecause(reason);
	}

	static String readyReason(FillReviewResponse result) {
		if (result.filled().isEmpty() && result.aiAnswered().isEmpty() && result.note() != null) {
			// nothing filled: the site wants a login first, or the form is elsewhere; the worker says which
			return result.note();
		}
		if (result.leftForYou().isEmpty()) {
			return READY + " everything is filled; only Submit is left.";
		}
		return READY + " filled " + (result.filled().size() + result.aiAnswered().size())
				+ " fields; still to answer: " + String.join(", ", result.leftForYou()) + ".";
	}

	private static FillReviewResponse read(JsonNode reply) {
		if (reply == null) {
			return new FillReviewResponse(false, List.of(), Map.of(), List.of(), "The apply worker gave no answer.");
		}
		Map<String, String> ai = new LinkedHashMap<>();
		reply.path("aiAnswered").properties().forEach(e -> ai.put(e.getKey(), e.getValue().asString()));
		return new FillReviewResponse(reply.path("opened").asBoolean(false), strings(reply.path("filled")), ai,
				strings(reply.path("leftForYou")), reply.path("note").isString() ? reply.path("note").asString() : null);
	}

	private static List<String> strings(JsonNode array) {
		List<String> out = new ArrayList<>();
		array.forEach(item -> out.add(item.asString()));
		return out;
	}

	/** Another user's application is "not found": its existence isn't revealed. */
	private Application load(String userId, String id) {
		return applications.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new NotFoundException("No application " + id + "."));
	}

	private record Job(String url, Map<String, String> selectors, Map<String, String> answers) {
	}

}
