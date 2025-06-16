package com.fitness.userservice.service;

import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.model.User;
import com.fitness.userservice.model.UserRole;
import com.fitness.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor; // For constructor injection
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
    public UserResponse register(RegisterRequest request) {
        log.info("Register request received for email: {}, keycloakId: {}", request.getEmail(), request.getKeycloakId());

        //Check if a user already exists with this Keycloak ID.
        if (request.getKeycloakId() != null) {
            Optional<User> userByKeycloakIdOpt = userRepository.findByKeycloakId(request.getKeycloakId());
            if (userByKeycloakIdOpt.isPresent()) {
                User existingUser = userByKeycloakIdOpt.get();
                log.info("User found by Keycloak ID: {}. Returning existing user.", request.getKeycloakId());
                // Optionally: Update email/firstName/lastName if they differ from Keycloak claims, if desired.
                // For now, just return the existing user.
                boolean changed = false;
                if (request.getEmail() != null && !request.getEmail().equals(existingUser.getEmail())) {
                    // Before changing email, check if the new email is already taken by ANOTHER user
                    if (userRepository.existsByEmail(request.getEmail()) &&
                            !userRepository.findByEmail(request.getEmail()).get().getKeycloakId().equals(request.getKeycloakId())) {
                        log.warn("USER-SERVICE: Attempt to update email to {} for Keycloak ID {}, but this email is already used by another account.", request.getEmail(), request.getKeycloakId());
                        // Handle this conflict: throw exception or ignore email update
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
                if(changed) {
                    userRepository.save(existingUser);
                }
                return mapToUserResponse(existingUser);
            }
        }

        // Step 2: If no user found by Keycloak ID, check by email.
        Optional<User> userByEmailOpt = userRepository.findByEmail(request.getEmail());
        if (userByEmailOpt.isPresent()) {
            User existingUserByEmail = userByEmailOpt.get();
            log.info("User found by email: {}. Current Keycloak ID in DB: {}",
                    request.getEmail(), existingUserByEmail.getKeycloakId());

            // If the user found by email does not have a keycloakId OR
            // if their existing keycloakId is different (and you decide to overwrite - be cautious),
            // then update it. For now, we'll only set it if it's null.
            if (existingUserByEmail.getKeycloakId() == null) {
                log.info("Updating existing user (email: {}) with Keycloak ID: {}",
                        request.getEmail(), request.getKeycloakId());
                existingUserByEmail.setKeycloakId(request.getKeycloakId());
                // Optionally update firstName/lastName if they are blank in DB but present in request
                if (isEffectivelyBlank(existingUserByEmail.getFirstName()) && !isEffectivelyBlank(request.getFirstName())) {
                    existingUserByEmail.setFirstName(request.getFirstName());
                }
                if (isEffectivelyBlank(existingUserByEmail.getLastName()) && !isEffectivelyBlank(request.getLastName())) {
                    existingUserByEmail.setLastName(request.getLastName());
                }
                User updatedUser = userRepository.save(existingUserByEmail);
                return mapToUserResponse(updatedUser);
            } else if (request.getKeycloakId() != null && !request.getKeycloakId().trim().isEmpty() && !existingUserByEmail.getKeycloakId().equals(request.getKeycloakId())) {
                log.warn("USER-SERVICE: Conflict! Email [{}] is already linked to a different Keycloak ID [{}]. Requested Keycloak ID was [{}].",
                        request.getEmail(), existingUserByEmail.getKeycloakId(), request.getKeycloakId());
                // This is a significant conflict. How to resolve depends on business rules.
                // Throwing an exception might be appropriate.
                throw new IllegalStateException("Email " + request.getEmail() + " is already associated with a different authenticated account.");
            } else {
                // Email matches, and Keycloak ID also matches. User is already correctly synced.
                log.info("User (email: {}) already correctly synced with Keycloak ID: {}",
                        request.getEmail(), request.getKeycloakId());
                return mapToUserResponse(existingUserByEmail);
            }
        }

        // Step 3: If no user by Keycloak ID and no user by email, create a new user.
        log.info("No existing user found by Keycloak ID or email. Creating new user for email: {} with Keycloak ID: {}",
                request.getEmail(), request.getKeycloakId());
        User newUser = new User();
        newUser.setEmail(request.getEmail());
        newUser.setKeycloakId(request.getKeycloakId());
        newUser.setFirstName(request.getFirstName());
        newUser.setLastName(request.getLastName());
        newUser.setPassword(passwordEncoder.encode(request.getPassword()));
        // Set default role if applicable
        newUser.setRole(UserRole.USER);

        User savedUser = userRepository.save(newUser);
        log.info("New user created with ID: {} and Keycloak ID: {}", savedUser.getId(), savedUser.getKeycloakId());
        return mapToUserResponse(savedUser);
    }

    private boolean isEffectivelyBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private UserResponse mapToUserResponse(User user) {
        UserResponse userResponse = new UserResponse();
        userResponse.setId(user.getId());
        userResponse.setKeycloakId(user.getKeycloakId());
        userResponse.setEmail(user.getEmail());
        // userResponse.setPassword(user.getPassword()); // Password should NOT be in response
        userResponse.setFirstName(user.getFirstName());
        userResponse.setLastName(user.getLastName());
        // userResponse.setRole(user.getRole()); // If you have roles
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

    // This method is called with Keycloak ID from the gateway
    @Transactional(readOnly = true)
    public Boolean existByUserId(String keycloakId) {
        log.info("UserService (USER-SERVICE): Checking existence by Keycloak ID: {}", keycloakId);
        return userRepository.existsByKeycloakId(keycloakId);
    }
}