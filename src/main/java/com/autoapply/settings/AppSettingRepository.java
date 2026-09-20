package com.autoapply.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
    List<AppSetting> findByCategoryOrderByDisplayOrderAscKeyAsc(String category);
    List<AppSetting> findAllByOrderByCategoryAscDisplayOrderAscKeyAsc();
}
