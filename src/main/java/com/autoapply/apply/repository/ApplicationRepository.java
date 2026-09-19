package com.autoapply.apply.repository;
import com.autoapply.apply.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface ApplicationRepository extends JpaRepository<Application, String> {
    List<Application> findByUserIdOrderByAppliedAtDesc(String userId);
    boolean existsByUserIdAndJobId(String userId, String jobId);
    long countByUserIdAndAppliedAtAfter(String userId, LocalDateTime after);
    List<Application> findByUserIdAndStatus(String userId, String status);
}
