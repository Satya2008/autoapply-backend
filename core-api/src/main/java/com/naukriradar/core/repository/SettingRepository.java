package com.naukriradar.core.repository;

import com.naukriradar.core.model.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, String> {
}
