package com.naukriradar.matching.service;

import java.util.List;

import com.naukriradar.common.exception.NotFoundException;
import com.naukriradar.matching.dto.response.MatchDetailResponse;
import com.naukriradar.matching.dto.response.MatchPageResponse;
import com.naukriradar.matching.mapper.MatchMapper;
import com.naukriradar.matching.model.JobMatch;
import com.naukriradar.matching.repository.JobMatchRepository;
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

	/** Best matches first, keyset-paged on (score, id). */
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

	/** Another user's match is "not found", not "forbidden": its existence isn't revealed. */
	@Transactional(readOnly = true)
	public MatchDetailResponse get(String userId, String matchId) {
		return repository.findByIdAndUserId(matchId, userId)
				.map(mapper::toDetail)
				.orElseThrow(() -> new NotFoundException("No match " + matchId + "."));
	}

}
