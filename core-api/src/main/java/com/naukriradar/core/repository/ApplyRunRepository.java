package com.naukriradar.core.repository;

import java.util.List;
import java.util.Optional;

import com.naukriradar.core.model.ApplyRun;
import com.naukriradar.core.model.ApplyRunStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplyRunRepository extends JpaRepository<ApplyRun, String> {

	Optional<ApplyRun> findByIdAndUserId(String id, String userId);

	List<ApplyRun> findByStatus(ApplyRunStatus status);

}
