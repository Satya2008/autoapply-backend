package com.naukriradar.core.service;

import java.util.Locale;

import com.naukriradar.common.exception.ConflictException;
import com.naukriradar.core.dto.request.CreateUserRequest;
import com.naukriradar.core.dto.response.UserResponse;
import com.naukriradar.core.mapper.ProfileMapper;
import com.naukriradar.core.model.Profile;
import com.naukriradar.core.model.User;
import com.naukriradar.core.repository.ProfileRepository;
import com.naukriradar.core.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;
	private final ProfileRepository profileRepository;
	private final ProfileMapper mapper;

	/** Creates the user and an empty profile together, so a user always has one. */
	@Transactional
	public UserResponse createUser(CreateUserRequest request) {
		String email = request.email().strip().toLowerCase(Locale.ROOT);
		if (userRepository.existsByEmail(email)) {
			throw duplicateEmail();
		}
		User user;
		try {
			// flush here so a parallel signup with the same email fails on the unique index now
			user = userRepository.saveAndFlush(new User(email));
		}
		catch (DataIntegrityViolationException ex) {
			throw duplicateEmail();
		}
		profileRepository.save(new Profile(user));
		return mapper.toResponse(user);
	}

	private static ConflictException duplicateEmail() {
		return new ConflictException("A user with this email already exists.");
	}

}
