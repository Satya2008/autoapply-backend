package com.naukriradar.core.repository;

import java.util.Optional;

import com.naukriradar.core.model.NotificationPreference;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, String> {

	Optional<NotificationPreference> findByTelegramLinkCode(String code);

}
