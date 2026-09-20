package com.autoapply.matching.repository;

import com.autoapply.matching.entity.JobMatch;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JobMatchRepository extends JpaRepository<JobMatch, String> {

    List<JobMatch> findByUserIdOrderByMatchScoreDesc(String userId);

    Page<JobMatch> findByUserIdOrderByMatchScoreDesc(String userId, Pageable pageable);

    List<JobMatch> findByUserIdAndRecommendedOrderByMatchScoreDesc(String userId, Boolean recommended);

    Optional<JobMatch> findByUserIdAndJobId(String userId, String jobId);

    boolean existsByUserIdAndJobId(String userId, String jobId);

    long countByUserId(String userId);

    long countByRecommendedTrue();

    long countByMatchedAtAfter(LocalDateTime after);

    @Query("SELECT m FROM JobMatch m WHERE m.userId = :userId AND m.matchScore >= :minScore " +
            "AND m.status = 'MATCHED' ORDER BY m.matchScore DESC")
    List<JobMatch> findApplicable(@Param("userId") String userId, @Param("minScore") double minScore);

    @Query("SELECT AVG(m.matchScore) FROM JobMatch m")
    Double averageScore();
}
