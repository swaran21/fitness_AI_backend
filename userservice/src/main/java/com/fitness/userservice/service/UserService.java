package com.fitness.userservice.service;

import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.model.User;
import com.fitness.userservice.model.UserRole;
import com.fitness.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse ensureUserExists(String authProviderId) {
        log.info("Ensuring user exists for authProviderId: {}", authProviderId);

        Optional<User> existingUserOpt = userRepository.findByAuthProviderId(authProviderId);

        if (existingUserOpt.isPresent()) {
            log.info("User found for authProviderId: {}. Returning existing user.", authProviderId);
            return mapToUserResponse(existingUserOpt.get());
        } else {
            log.info("No user found for authProviderId: {}. Creating a new user record.", authProviderId);
            User newUser = new User();
            newUser.setAuthProviderId(authProviderId);
            newUser.setRole(UserRole.USER);
            newUser.setEmail(authProviderId + "@placeholder.auth.com");

            User savedUser = userRepository.save(newUser);
            log.info("New user provisioned with internal ID: {} for authProviderId: {}", savedUser.getId(), savedUser.getAuthProviderId());
            return mapToUserResponse(savedUser);
        }
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.info("Register request received for email: {}, authProviderId: {}", request.getEmail(), request.getAuthProviderId());

        if (request.getAuthProviderId() != null) {
            Optional<User> userByAuthProviderIdOpt = userRepository.findByAuthProviderId(request.getAuthProviderId());
            if (userByAuthProviderIdOpt.isPresent()) {
                User existingUser = userByAuthProviderIdOpt.get();
                log.info("User found by Auth Provider ID: {}. Returning existing user.", request.getAuthProviderId());
                boolean changed = false;

                if (request.getEmail() != null && !request.getEmail().equals(existingUser.getEmail())) {
                    if (userRepository.existsByEmail(request.getEmail()) &&
                            !userRepository.findByEmail(request.getEmail()).get().getAuthProviderId().equals(request.getAuthProviderId())) {
                        log.warn("USER-SERVICE: Attempt to update email to {} for authProviderId {}, but this email is already used by another account.",
                                request.getEmail(), request.getAuthProviderId());
                        // Handle conflict here
                    } else {
                        existingUser.setEmail(request.getEmail());
                        changed = true;
                    }
                }
                if (request.getFirstName() != null && !request.getFirstName().equals(existingUser.getFirstName())) {
                    existingUser.setFirstName(request.getFirstName());
                    changed = true;
                }
                if (request.getLastName() != null && !request.getLastName().equals(existingUser.getLastName())) {
                    existingUser.setLastName(request.getLastName());
                    changed = true;
                }
                if (changed) {
                    userRepository.save(existingUser);
                }
                return mapToUserResponse(existingUser);
            }
        }

        Optional<User> userByEmailOpt = userRepository.findByEmail(request.getEmail());
        if (userByEmailOpt.isPresent()) {
            User existingUserByEmail = userByEmailOpt.get();
            log.info("User found by email: {}. Current authProviderId in DB: {}",
                    request.getEmail(), existingUserByEmail.getAuthProviderId());

            if (existingUserByEmail.getAuthProviderId() == null) {
                log.info("Updating existing user (email: {}) with authProviderId: {}",
                        request.getEmail(), request.getAuthProviderId());
                existingUserByEmail.setAuthProviderId(request.getAuthProviderId());

                if (isEffectivelyBlank(existingUserByEmail.getFirstName()) && !isEffectivelyBlank(request.getFirstName())) {
                    existingUserByEmail.setFirstName(request.getFirstName());
                }
                if (isEffectivelyBlank(existingUserByEmail.getLastName()) && !isEffectivelyBlank(request.getLastName())) {
                    existingUserByEmail.setLastName(request.getLastName());
                }
                User updatedUser = userRepository.save(existingUserByEmail);
                return mapToUserResponse(updatedUser);
            } else if (request.getAuthProviderId() != null && !request.getAuthProviderId().trim().isEmpty()
                    && !existingUserByEmail.getAuthProviderId().equals(request.getAuthProviderId())) {
                log.warn("USER-SERVICE: Conflict! Email [{}] is already linked to a different authProviderId [{}]. Requested authProviderId was [{}].",
                        request.getEmail(), existingUserByEmail.getAuthProviderId(), request.getAuthProviderId());
                throw new IllegalStateException("Email " + request.getEmail() + " is already associated with a different authenticated account.");
            } else {
                log.info("User (email: {}) already correctly synced with authProviderId: {}",
                        request.getEmail(), request.getAuthProviderId());
                return mapToUserResponse(existingUserByEmail);
            }
        }

        log.info("No existing user found by authProviderId or email. Creating new user for email: {} with authProviderId: {}",
                request.getEmail(), request.getAuthProviderId());
        User newUser = new User();
        newUser.setEmail(request.getEmail());
        newUser.setAuthProviderId(request.getAuthProviderId());
        newUser.setFirstName(request.getFirstName());
        newUser.setLastName(request.getLastName());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        newUser.setRole(UserRole.USER);

        User savedUser = userRepository.save(newUser);
        log.info("New user created with ID: {} and authProviderId: {}", savedUser.getId(), savedUser.getAuthProviderId());
        return mapToUserResponse(savedUser);
    }

    private boolean isEffectivelyBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private UserResponse mapToUserResponse(User user) {
        UserResponse userResponse = new UserResponse();
        userResponse.setId(user.getId());
        userResponse.setAuthProviderId(user.getAuthProviderId());
        userResponse.setEmail(user.getEmail());
        userResponse.setFirstName(user.getFirstName());
        userResponse.setLastName(user.getLastName());
        userResponse.setCreated(user.getCreated());
        userResponse.setModified(user.getModified());
        return userResponse;
    }

    @Transactional(readOnly = true)
    public UserResponse getUserProfile(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with internal id: " + userId));
        return mapToUserResponse(user);
    }

    @Transactional(readOnly = true)
    public Boolean existByUserId(String authProviderId) {
        log.info("UserService (USER-SERVICE): Checking existence by authProviderId: {}", authProviderId);
        return userRepository.existsByAuthProviderId(authProviderId);
    }
}
