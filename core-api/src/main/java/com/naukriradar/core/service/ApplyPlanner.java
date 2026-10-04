package com.naukriradar.core.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.client.MatchForApply;
import com.naukriradar.core.config.ApplicationProperties;
import com.naukriradar.core.model.Application;
import com.naukriradar.core.model.ApplicationStatus;
import com.naukriradar.core.model.PortalConfig;
import com.naukriradar.core.model.Profile;
import com.naukriradar.core.model.RiskBand;
import com.naukriradar.core.repository.ApplicationRepository;
import com.naukriradar.core.repository.PortalConfigRepository;
import com.naukriradar.core.repository.ProfileRepository;
import com.naukriradar.core.settings.SettingDefinitions;
import com.naukriradar.core.settings.Settings;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a user's matches into applications. Each match is routed one of two ways:
 *
 * <ul>
 * <li>QUEUED for the apply engine, only if auto apply is on, the site is LOW risk and the
 * daily limit has room;</li>
 * <li>NEEDS_YOU otherwise, with the form answers prepared so the user can apply by hand.</li>
 * </ul>
 *
 * The profile row is locked (SELECT ... FOR UPDATE) for the whole plan, so two plans for the
 * same user run one after the other and the second sees what the first created. The unique
 * (user_id, job_id) key is the backstop if anything ever slips past.
 */
@Service
public class ApplyPlanner {

	private final ProfileRepository profileRepository;
	private final ApplicationRepository applicationRepository;
	private final PortalConfigRepository portalRepository;
	private final RiskClassifier riskClassifier;
	private final PrefillService prefillService;
	private final ApplicationStateMachine stateMachine;
	private final ApplicationProperties properties;
	private final Settings settings;
	private final JsonMapper jsonMapper;
	private final Clock clock = Clock.systemUTC();

	public ApplyPlanner(ProfileRepository profileRepository, ApplicationRepository applicationRepository,
			PortalConfigRepository portalRepository, RiskClassifier riskClassifier, PrefillService prefillService,
			ApplicationStateMachine stateMachine, ApplicationProperties properties, Settings settings, JsonMapper jsonMapper) {
		this.profileRepository = profileRepository;
		this.applicationRepository = applicationRepository;
		this.portalRepository = portalRepository;
		this.riskClassifier = riskClassifier;
		this.prefillService = prefillService;
		this.stateMachine = stateMachine;
		this.properties = properties;
		this.settings = settings;
		this.jsonMapper = jsonMapper;
	}

	@Transactional
	public PlanResult plan(String userId, List<MatchForApply> matches) {
		Profile profile = profileRepository.findForUpdate(userId)
				.orElseThrow(() -> new NotFoundException("No profile for user " + userId + "."));

		List<MatchForApply> unique = dedupe(matches);
		Set<String> existing = unique.isEmpty() ? Set.of()
				: applicationRepository.findJobIds(userId, unique.stream().map(MatchForApply::jobId).toList());
		List<PortalConfig> portals = portalRepository.findByEnabledTrue();
		RiskClassifier.Rules rules = riskClassifier.rules();
		int maxNeedsYou = settings.getInt(SettingDefinitions.MAX_NEEDS_YOU_PER_RUN);
		Instant startOfDay = LocalDate.now(clock.withZone(properties.zone())).atStartOfDay(properties.zone()).toInstant();
		long automatedToday = applicationRepository.countByUserIdAndAutomatedTrueAndCreatedAtGreaterThanEqual(userId, startOfDay);
		String prefill = jsonMapper.writeValueAsString(prefillService.answers(profile));

		List<String> queued = new ArrayList<>();
		int needsYou = 0;
		int alreadyApplied = 0;
		int belowScore = 0;
		int deferred = 0;
		for (MatchForApply match : unique) {
			if (existing.contains(match.jobId())) {
				alreadyApplied++;
				continue;
			}
			if (match.score() < profile.getMinMatchScore()) {
				belowScore++;
				continue;
			}
			RiskClassifier.Risk risk = rules.classify(match.applyUrl(), portals);
			String reasonForUser = needsYouReason(profile, risk, automatedToday);
			if (reasonForUser != null && needsYou >= maxNeedsYou) {
				deferred++;
				continue;
			}

			Application application = applicationRepository.save(new Application(userId, match.jobId(),
					truncate(match.title(), 300), truncate(match.company(), 200), truncate(match.location(), 200),
					match.applyUrl(), match.score(), risk.band(), truncate(risk.reason(), 300), prefill));
			stateMachine.created(application, "From a match scoring " + match.score() + ".");
			if (reasonForUser == null) {
				stateMachine.move(application, ApplicationStatus.QUEUED, risk.reason());
				application.markAutomated();
				automatedToday++;
				queued.add(application.getId());
			}
			else {
				stateMachine.move(application, ApplicationStatus.NEEDS_YOU, reasonForUser);
				application.needsYouBecause(reasonForUser);
				needsYou++;
			}
		}
		return new PlanResult(unique.size(), queued, needsYou, alreadyApplied, belowScore, deferred);
	}

	/** Null when the application can go to the engine; otherwise why the user must apply. */
	private String needsYouReason(Profile profile, RiskClassifier.Risk risk, long automatedToday) {
		if (risk.band() != RiskBand.LOW) {
			return risk.reason();
		}
		if (!profile.isAutoApplyEnabled()) {
			return "Auto apply is off, so this is ready for you to send.";
		}
		if (automatedToday >= profile.getDailyApplyLimit()) {
			return "Today's limit of " + profile.getDailyApplyLimit() + " automatic applications is reached.";
		}
		return null;
	}

	/** matching-service shouldn't send a job twice, but don't trust that with a unique key at stake. */
	private static List<MatchForApply> dedupe(List<MatchForApply> matches) {
		Set<String> seen = new LinkedHashSet<>();
		return matches.stream()
				.filter(m -> m.jobId() != null && m.applyUrl() != null && m.title() != null && m.company() != null)
				.filter(m -> seen.add(m.jobId()))
				.toList();
	}

	private static String truncate(String value, int max) {
		return value == null || value.length() <= max ? value : value.substring(0, max);
	}

	/**
	 * @param queued ids of applications sent to the engine
	 * @param deferred left for a later run because of the needs-you cap
	 */
	public record PlanResult(int considered, List<String> queued, int needsYou, int alreadyApplied, int belowScore,
			int deferred) {
	}

}
