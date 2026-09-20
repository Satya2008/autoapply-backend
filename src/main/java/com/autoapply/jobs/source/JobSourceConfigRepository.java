package com.autoapply.jobs.source;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobSourceConfigRepository extends JpaRepository<JobSourceConfig, String> {
    Optional<JobSourceConfig> findByCode(String code);
    List<JobSourceConfig> findByEnabledTrueOrderByPriorityAsc();
    List<JobSourceConfig> findAllByOrderByPriorityAscNameAsc();
    boolean existsByCode(String code);
}
