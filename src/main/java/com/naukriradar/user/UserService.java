package com.naukriradar.user;

import java.util.Locale;

import com.naukriradar.common.ConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository users;
	private final ProfileRepository profiles;
	private final ProfileMapper mapper;

	/** Creates a user together with an empty profile, so every user always has one. */
	@Transactional
	public UserResponse createUser(CreateUserRequest request) {
		String email = request.email().trim().toLowerCase(Locale.ROOT);
		if (users.existsByEmail(email)) {
			throw duplicateEmail();
		}
		User user;
		try {
			// Flush now so a concurrent insert of the same email fails here, on the unique index.
			user = users.saveAndFlush(new User(email));
		}
		catch (DataIntegrityViolationException ex) {
			throw duplicateEmail();
		}
		profiles.save(new Profile(user));
		return mapper.toResponse(user);
	}

	private static ConflictException duplicateEmail() {
		return new ConflictException("A user with this email already exists.");
	}

}
