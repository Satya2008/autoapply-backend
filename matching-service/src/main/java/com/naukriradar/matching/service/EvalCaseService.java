package com.naukriradar.matching.service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.matching.client.CandidateJob;
import com.naukriradar.matching.client.MatchingProfile;
import com.naukriradar.matching.dto.request.EvalCaseRequest;
import com.naukriradar.matching.dto.response.EvalCaseResponse;
import com.naukriradar.matching.model.EvalCase;
import com.naukriradar.matching.repository.EvalCaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** The golden set: hand-scored (profile, job) pairs that every eval runs on. */
@Service
public class EvalCaseService {

	private static final Logger log = LoggerFactory.getLogger(EvalCaseService.class);

	private final EvalCaseRepository repository;
	private final JsonMapper json;
	private final Clock clock = Clock.systemUTC();

	public EvalCaseService(EvalCaseRepository repository, JsonMapper json) {
		this.repository = repository;
		this.json = json;
	}

	@Transactional(readOnly = true)
	public List<EvalCaseResponse> list() {
		return repository.findAllByOrderByNameAsc().stream().map(this::toResponse).toList();
	}

	@Transactional(readOnly = true)
	public List<EvalCase> all() {
		return repository.findAllByOrderByNameAsc();
	}

	@Transactional
	public EvalCaseResponse create(EvalCaseRequest request) {
		String name = request.name().strip();
		if (repository.existsByName(name)) {
			throw new ConflictException("An eval case named " + name + " already exists.");
		}
		EvalCase evalCase = new EvalCase(name, json.writeValueAsString(request.profile()), json.writeValueAsString(request.job()),
				request.expectedScore(), request.notes(), clock.instant());
		try {
			return toResponse(repository.saveAndFlush(evalCase));
		}
		catch (DataIntegrityViolationException ex) {
			throw new ConflictException("An eval case named " + name + " already exists.");
		}
	}

	@Transactional
	public void delete(String id) {
		EvalCase evalCase = repository.findById(id).orElseThrow(() -> new NotFoundException("No eval case " + id + "."));
		repository.delete(evalCase);
	}

	/** The candidate of a case, as matching sees a real profile. */
	public MatchingProfile profileOf(EvalCase evalCase) {
		EvalCaseRequest.Profile p = json.readValue(evalCase.getProfileJson(), EvalCaseRequest.Profile.class);
		return new MatchingProfile("eval-" + evalCase.getId(), p.skills(), p.targetRoles(), p.preferredLocations(),
				Boolean.TRUE.equals(p.remoteOk()), null, p.experienceYears(), List.of(), List.of(), 0);
	}

	/** The job of a case, posted yesterday, so recency is the same for every case and every run. */
	public CandidateJob jobOf(EvalCase evalCase, Instant now) {
		EvalCaseRequest.Job j = json.readValue(evalCase.getJobJson(), EvalCaseRequest.Job.class);
		return new CandidateJob(evalCase.getId(), j.title(), j.company(), j.location(), Boolean.TRUE.equals(j.remote()), null,
				null, null, now.minus(Duration.ofDays(1)), null, j.description(), j.requiredSkills(), j.minYearsExperience(),
				j.seniority());
	}

	/** On the first start, the built-in golden set; after that the admin's set is left alone. */
	@EventListener(ApplicationReadyEvent.class)
	@Transactional
	public void seedGoldenSet() throws IOException {
		if (repository.count() > 0) {
			return;
		}
		List<EvalCaseRequest> cases;
		try (InputStream in = new ClassPathResource("evals/golden-set.json").getInputStream()) {
			cases = json.readValue(in, new TypeReference<>() {
			});
		}
		Instant now = clock.instant();
		for (EvalCaseRequest request : cases) {
			repository.save(new EvalCase(request.name(), json.writeValueAsString(request.profile()),
					json.writeValueAsString(request.job()), request.expectedScore(), request.notes(), now));
		}
		log.info("Added the built-in golden set: {} eval case(s)", cases.size());
	}

	private EvalCaseResponse toResponse(EvalCase evalCase) {
		return new EvalCaseResponse(evalCase.getId(), evalCase.getName(),
				json.readValue(evalCase.getProfileJson(), EvalCaseRequest.Profile.class),
				json.readValue(evalCase.getJobJson(), EvalCaseRequest.Job.class), evalCase.getExpectedScore(),
				evalCase.getNotes(), evalCase.getCreatedAt());
	}

}
