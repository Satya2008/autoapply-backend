package com.autoapply.apply.repository;

import com.autoapply.apply.entity.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, String> {

    List<Application> findByUserIdOrderByAppliedAtDesc(String userId);

    Page<Application> findByUserIdOrderByAppliedAtDesc(String userId, Pageable pageable);

    Page<Application> findAllByOrderByAppliedAtDesc(Pageable pageable);

    boolean existsByUserIdAndJobId(String userId, String jobId);

    long countByUserIdAndAppliedAtAfter(String userId, LocalDateTime after);

    long countByStatus(String status);

    long countByAppliedAtAfter(LocalDateTime after);

    List<Application> findTop10ByOrderByAppliedAtDesc();

    @Query("SELECT a FROM Application a WHERE a.status = 'RETRY_SCHEDULED' AND a.nextRetryAt <= :now")
    List<Application> findDueForRetry(@Param("now") LocalDateTime now);

    @Query("SELECT a.status, COUNT(a) FROM Application a GROUP BY a.status")
    List<Object[]> countGroupedByStatus();

    @Query("SELECT FUNCTION('DATE', a.appliedAt), COUNT(a) FROM Application a " +
            "WHERE a.appliedAt >= :since GROUP BY FUNCTION('DATE', a.appliedAt) ORDER BY FUNCTION('DATE', a.appliedAt)")
    List<Object[]> countPerDaySince(@Param("since") LocalDateTime since);
}
