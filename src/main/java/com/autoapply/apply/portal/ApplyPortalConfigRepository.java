package com.autoapply.apply.portal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApplyPortalConfigRepository extends JpaRepository<ApplyPortalConfig, String> {
    List<ApplyPortalConfig> findByEnabledTrueOrderByPriorityAsc();
    List<ApplyPortalConfig> findAllByOrderByPriorityAscNameAsc();
    Optional<ApplyPortalConfig> findByCode(String code);
}
