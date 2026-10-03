package com.naukriradar.matching.repository;

import java.util.List;
import java.util.Optional;

import com.naukriradar.matching.model.JobMatch;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobMatchRepository extends JpaRepository<JobMatch, String> {

	Optional<JobMatch> findByIdAndUserId(String id, String userId);

	long countByUserId(String userId);

	@Query("""
			select m from JobMatch m
			where m.userId = :userId and m.score >= :minScore
			order by m.score desc, m.id desc""")
	List<JobMatch> firstPage(@Param("userId") String userId, @Param("minScore") int minScore, Pageable page);

	/** Rows after (score, id) in the same order: keyset paging on idx_job_matches_user_score. */
	@Query("""
			select m from JobMatch m
			where m.userId = :userId and m.score >= :minScore
			  and (m.score < :afterScore or (m.score = :afterScore and m.id < :afterId))
			order by m.score desc, m.id desc""")
	List<JobMatch> pageAfter(@Param("userId") String userId, @Param("minScore") int minScore,
			@Param("afterScore") int afterScore, @Param("afterId") String afterId, Pageable page);

}
