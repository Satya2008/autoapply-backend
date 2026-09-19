package com.autoapply.user.service;

import com.autoapply.user.dto.*;
import com.autoapply.user.entity.User;
import com.autoapply.user.repository.UserRepository;
import com.autoapply.config.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }
        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .phone(request.getPhone())
                .location(request.getLocation())
                .currentRole(request.getCurrentRole())
                .experienceYears(request.getExperienceYears())
                .expectedSalary(request.getExpectedSalary())
                .preferredJobType(request.getPreferredJobType())
                .skills(request.getSkills())
                .targetRoles(request.getTargetRoles())
                .build();

        user = userRepository.save(user);
        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getFullName());
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid credentials");
        }
        String token = jwtService.generateToken(user.getEmail());
        return new AuthResponse(token, user.getId(), user.getEmail(), user.getFullName());
    }

    public User getProfile(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
