package com.naukriradar.user;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProfileRepository extends JpaRepository<Profile, UUID> {
}
