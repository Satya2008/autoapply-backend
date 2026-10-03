package com.naukriradar.job.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.naukriradar.common.exception.BadRequestException;
import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.job.dto.request.JobSourceRequest;
import com.naukriradar.job.dto.response.JobSourceResponse;
import com.naukriradar.job.mapper.JobSourceMapper;
import com.naukriradar.job.model.JobSource;
import com.naukriradar.job.repository.JobRepository;
import com.naukriradar.job.repository.JobSourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin CRUD for job sources. Jobs already fetched stay when their source is deleted. */
@Service
@RequiredArgsConstructor
public class JobSourceService {

	private final JobSourceRepository sourceRepository;
	private final JobRepository jobRepository;
	private final JobSourceValidator validator;
	private final JobSourceMapper mapper;
	private final SourceRunLocks runLocks;

	@Transactional(readOnly = true)
	public List<JobSourceResponse> list() {
		Map<String, Long> counts = jobRepository.countPerSource().stream()
				.collect(Collectors.toMap(JobRepository.SourceJobCount::getSourceCode, JobRepository.SourceJobCount::getTotal));
		return sourceRepository.findAllByOrderByPriorityDescCodeAsc().stream()
				.map(source -> mapper.toResponse(source, counts.getOrDefault(source.getCode(), 0L)))
				.toList();
	}

	@Transactional(readOnly = true)
	public JobSourceResponse get(String id) {
		JobSource source = load(id);
		return mapper.toResponse(source, jobRepository.countBySourceCode(source.getCode()));
	}

	@Transactional
	public JobSourceResponse create(JobSourceRequest request) {
		validator.validate(request);
		if (sourceRepository.existsByCode(request.code())) {
			throw duplicateCode(request.code());
		}
		JobSource source = new JobSource(request.code());
		mapper.apply(request, source);
		try {
			sourceRepository.saveAndFlush(source);
		}
		catch (DataIntegrityViolationException ex) {
			throw duplicateCode(request.code());
		}
		return mapper.toResponse(source, 0);
	}

	@Transactional
	public JobSourceResponse update(String id, JobSourceRequest request) {
		JobSource source = load(id);
		if (!source.getCode().equals(request.code())) {
			// jobs carry the code; renaming it would orphan them
			throw new BadRequestException("code can't be changed (it is '" + source.getCode() + "').");
		}
		validator.validate(request);
		mapper.apply(request, source);
		sourceRepository.saveAndFlush(source);
		return mapper.toResponse(source, jobRepository.countBySourceCode(source.getCode()));
	}

	@Transactional
	public void delete(String id) {
		JobSource source = load(id);
		if (runLocks.isRunning(source.getId())) {
			throw new ConflictException("A fetch for this source is running. Try again when it finishes.");
		}
		sourceRepository.delete(source);
	}

	JobSource load(String id) {
		return sourceRepository.findById(id)
				.orElseThrow(() -> new NotFoundException("No job source " + id + "."));
	}

	private static ConflictException duplicateCode(String code) {
		return new ConflictException("A job source with code '" + code + "' already exists.");
	}

}
