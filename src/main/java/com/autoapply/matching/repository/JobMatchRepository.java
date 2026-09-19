package com.autoapply.matching.repository;
import com.autoapply.matching.entity.JobMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JobMatchRepository extends JpaRepository<JobMatch, String> {
    List<JobMatch> findByUserIdOrderByMatchScoreDesc(String userId);
    List<JobMatch> findByUserIdAndRecommended(String userId, Boolean recommended);
    boolean existsByUserIdAndJobId(String userId, String jobId);
}
