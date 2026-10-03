package com.naukriradar.core.repository;

import com.naukriradar.core.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, String> {

	boolean existsByEmail(String email);

}
