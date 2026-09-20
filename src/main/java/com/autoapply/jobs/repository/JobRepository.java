package com.autoapply.jobs.repository;

import com.autoapply.jobs.entity.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, String> {

    @Query("SELECT j FROM Job j WHERE " +
            "LOWER(j.jobTitle) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(j.employerName) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Job> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT j FROM Job j WHERE j.jobExpiresAt IS NULL OR j.jobExpiresAt > CURRENT_TIMESTAMP")
    List<Job> findActiveJobs();

    @Query("SELECT j FROM Job j WHERE j.jobExpiresAt IS NULL OR j.jobExpiresAt > CURRENT_TIMESTAMP")
    Page<Job> findActiveJobs(Pageable pageable);

    @Query("SELECT j FROM Job j ORDER BY j.jobPostedAt DESC")
    Page<Job> findRecent(Pageable pageable);

    boolean existsByFingerprint(String fingerprint);

    long countBySourceCode(String sourceCode);

    long countByFetchedAtAfter(LocalDateTime after);

    List<Job> findTop10ByOrderByFetchedAtDesc();

    @Modifying
    @Query("DELETE FROM Job j WHERE j.fetchedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT j.sourceCode, COUNT(j) FROM Job j GROUP BY j.sourceCode")
    List<Object[]> countGroupedBySource();
}
