package com.autoapply.jobs.repository;
import com.autoapply.jobs.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, String> {
    @Query("SELECT j FROM Job j WHERE j.jobTitle LIKE %:keyword% OR j.jobDescription LIKE %:keyword%")
    List<Job> searchByKeyword(String keyword);
    List<Job> findByJobPublisher(String publisher);
    @Query("SELECT j FROM Job j WHERE j.jobExpiresAt > CURRENT_TIMESTAMP OR j.jobExpiresAt IS NULL")
    List<Job> findActiveJobs();
}
