package com.naukriradar.core.repository;

import com.naukriradar.core.model.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileRepository extends JpaRepository<Profile, String> {
}
