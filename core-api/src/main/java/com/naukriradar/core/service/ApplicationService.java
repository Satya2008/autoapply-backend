package com.naukriradar.core.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.core.config.ApplicationProperties;
import com.naukriradar.core.dto.request.StatusUpdateRequest;
import com.naukriradar.core.dto.response.ApplicationDetailResponse;
import com.naukriradar.core.dto.response.ApplicationPageResponse;
import com.naukriradar.core.dto.response.ApplicationStatsResponse;
import com.naukriradar.core.dto.response.NeedsYouResponse;
import com.naukriradar.core.mapper.ApplicationMapper;
import com.naukriradar.core.model.Application;
import com.naukriradar.core.model.ApplicationStatus;
import com.naukriradar.core.model.SubmittedVia;
import com.naukriradar.core.repository.ApplicationEventRepository;
import com.naukriradar.core.repository.ApplicationRepository;
import com.naukriradar.core.repository.ProfileRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything a user does with their applications. "done" and "skip" are idempotent:
 * repeating them (a double click, a retried request) returns the same result instead of an
 * error. Concurrent changes to the same application are caught by its version column.
 */
@Service
public class ApplicationService {

	static final int NEEDS_YOU_LIMIT = 100;

	/** What a user may report through PATCH /status; the rest are set by the system. */
	private static final Set<ApplicationStatus> USER_REPORTABLE = EnumSet.of(ApplicationStatus.INTERVIEW,
			ApplicationStatus.OFFER, ApplicationStatus.REJECTED);

	/** Statuses that already mean "the user applied", so "done" has nothing left to do. */
	private static final Set<ApplicationStatus> ALREADY_DONE = EnumSet.of(ApplicationStatus.APPLIED,
			ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER, ApplicationStatus.REJECTED);

	private final ApplicationRepository repository;
	private final ApplicationEventRepository eventRepository;
	private final ProfileRepository profileRepository;
	private final ApplicationStateMachine stateMachine;
	private final ApplicationMapper mapper;
	private final ApplicationProperties properties;
	private final Clock clock = Clock.systemUTC();

	public ApplicationService(ApplicationRepository repository, ApplicationEventRepository eventRepository,
			ProfileRepository profileRepository, ApplicationStateMachine stateMachine, ApplicationMapper mapper,
			ApplicationProperties properties) {
		this.repository = repository;
		this.eventRepository = eventRepository;
		this.profileRepository = profileRepository;
		this.stateMachine = stateMachine;
		this.mapper = mapper;
		this.properties = properties;
	}

	/** Newest first, optionally one status; keyset-paged on the (time-ordered) id. */
	@Transactional(readOnly = true)
	public ApplicationPageResponse list(String userId, ApplicationStatus status, String cursor, int limit) {
		PageRequest page = PageRequest.of(0, limit + 1);
		List<Application> rows;
		if (cursor == null || cursor.isBlank()) {
			rows = repository.firstPage(userId, status, page);
		}
		else {
			rows = repository.pageAfter(userId, status, validCursor(cursor), page);
		}
		boolean more = rows.size() > limit;
		List<Application> items = more ? rows.subList(0, limit) : rows;
		String next = more ? items.get(items.size() - 1).getId() : null;
		return new ApplicationPageResponse(items.stream().map(mapper::toSummary).toList(), next);
	}

	@Transactional(readOnly = true)
	public ApplicationDetailResponse get(String userId, String id) {
		Application application = load(userId, id);
		return mapper.toDetail(application, eventRepository.findByApplicationIdOrderByAtAscIdAsc(id));
	}

	/** Best matches first, each with its ready answers. */
	@Transactional(readOnly = true)
	public List<NeedsYouResponse> needsYou(String userId) {
		return repository.findByUserIdAndStatusOrderByMatchScoreDescIdDesc(userId, ApplicationStatus.NEEDS_YOU,
				PageRequest.of(0, NEEDS_YOU_LIMIT)).stream()
				.map(mapper::toNeedsYou)
				.toList();
	}

	/** The user applied themselves. Calling it again changes nothing and returns the same. */
	@Transactional
	public ApplicationDetailResponse markDone(String userId, String id) {
		Application application = load(userId, id);
		if (!ALREADY_DONE.contains(application.getStatus())) {
			stateMachine.move(application, ApplicationStatus.APPLIED, "You applied yourself.");
			application.submittedVia(SubmittedVia.MANUAL);
		}
		return detail(application);
	}

	@Transactional
	public ApplicationDetailResponse skip(String userId, String id) {
		Application application = load(userId, id);
		stateMachine.move(application, ApplicationStatus.SKIPPED, "You skipped this one.");
		return detail(application);
	}

	/** INTERVIEW, OFFER or REJECTED, as reported by the user. Same status again is a no-op. */
	@Transactional
	public ApplicationDetailResponse updateStatus(String userId, String id, StatusUpdateRequest request) {
		if (!USER_REPORTABLE.contains(request.status())) {
			throw new BadRequestException("You can set INTERVIEW, OFFER or REJECTED; other statuses are set automatically.");
		}
		Application application = load(userId, id);
		String note = request.note() == null || request.note().isBlank() ? "Updated by you." : request.note().strip();
		stateMachine.move(application, request.status(), note);
		return detail(application);
	}

	@Transactional(readOnly = true)
	public ApplicationStatsResponse stats(String userId) {
		Map<ApplicationStatus, Long> byStatus = new EnumMap<>(ApplicationStatus.class);
		for (ApplicationStatus status : ApplicationStatus.values()) {
			byStatus.put(status, 0L);
		}
		repository.countByStatus(userId).forEach(c -> byStatus.put(c.getStatus(), c.getTotal()));
		long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
		long sent = ApplicationStatus.SENT.stream().mapToLong(byStatus::get).sum();
		Instant startOfDay = LocalDate.now(clock.withZone(properties.zone())).atStartOfDay(properties.zone()).toInstant();
		long automatedToday = repository.countByUserIdAndAutomatedTrueAndCreatedAtGreaterThanEqual(userId, startOfDay);
		int dailyLimit = profileRepository.findById(userId).map(p -> p.getDailyApplyLimit()).orElse(0);
		return new ApplicationStatsResponse(total, byStatus, sent, automatedToday, dailyLimit);
	}

	private ApplicationDetailResponse detail(Application application) {
		// flush first so the response shows the new version and the event just written
		repository.saveAndFlush(application);
		return mapper.toDetail(application, eventRepository.findByApplicationIdOrderByAtAscIdAsc(application.getId()));
	}

	/** Another user's application is "not found": its existence isn't revealed. */
	private Application load(String userId, String id) {
		return repository.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new NotFoundException("No application " + id + "."));
	}

	private static String validCursor(String cursor) {
		try {
			return UUID.fromString(cursor.strip()).toString();
		}
		catch (IllegalArgumentException ex) {
			throw new BadRequestException("Invalid cursor. Use the nextCursor value from the previous page.");
		}
	}

}
