package com.naukriradar.matching.service;

import java.util.List;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.matching.config.CacheConfig;
import com.naukriradar.matching.dto.response.MatchDetailResponse;
import com.naukriradar.matching.dto.response.MatchForApplyResponse;
import com.naukriradar.matching.dto.response.MatchPageResponse;
import com.naukriradar.matching.mapper.MatchMapper;
import com.naukriradar.matching.model.JobMatch;
import com.naukriradar.matching.repository.JobMatchRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchQueryService {

	private final JobMatchRepository repository;
	private final MatchMapper mapper;

	public MatchQueryService(JobMatchRepository repository, MatchMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
	}

	/**
	 * Best matches first, keyset-paged on (score, id). Pages are cached per user until the
	 * user's next match run; the key must start with the user id (see {@link MatchCache}).
	 */
	@Cacheable(cacheNames = CacheConfig.MATCH_PAGES, sync = true,
			key = "#userId + ':' + #minScore + ':' + #limit + ':' + (#cursor == null ? '' : #cursor.strip())")
	@Transactional(readOnly = true)
	public MatchPageResponse list(String userId, int minScore, String cursor, int limit) {
		PageRequest page = PageRequest.of(0, limit + 1);
		List<JobMatch> rows;
		if (cursor == null || cursor.isBlank()) {
			rows = repository.firstPage(userId, minScore, page);
		}
		else {
			MatchCursor after = MatchCursor.decode(cursor.strip());
			rows = repository.pageAfter(userId, minScore, after.score(), after.id(), page);
		}
		boolean more = rows.size() > limit;
		List<JobMatch> items = more ? rows.subList(0, limit) : rows;
		String next = null;
		if (more) {
			JobMatch last = items.get(items.size() - 1);
			next = new MatchCursor(last.getScore(), last.getId()).encode();
		}
		return new MatchPageResponse(items.stream().map(mapper::toSummary).toList(), next);
	}

	/** For core-api: the user's best matches at or above a score, best first. */
	@Transactional(readOnly = true)
	public List<MatchForApplyResponse> forApply(String userId, int minScore, int limit) {
		return repository.firstPage(userId, minScore, PageRequest.of(0, limit)).stream()
				.map(m -> new MatchForApplyResponse(m.getJobId(), m.getScore(), m.getJobTitle(), m.getJobCompany(),
						m.getJobLocation(), m.getJobApplyUrl()))
				.toList();
	}

	/** Another user's match is "not found", not "forbidden": its existence isn't revealed. */
	@Transactional(readOnly = true)
	public MatchDetailResponse get(String userId, String matchId) {
		return repository.findByIdAndUserId(matchId, userId)
				.map(mapper::toDetail)
				.orElseThrow(() -> new NotFoundException("No match " + matchId + "."));
	}

}
