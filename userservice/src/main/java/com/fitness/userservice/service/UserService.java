// In com.fitness.userservice.service.UserService.java

package com.fitness.userservice.service;

import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.model.User; // Assuming User entity has keycloakId field
import com.fitness.userservice.repository.UserRepository;
import lombok.RequiredArgsConstructor; // For constructor injection
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional; // Import

import java.util.Optional; // Import

@Service
@Slf4j
@RequiredArgsConstructor // Use constructor injection
public class UserService {

    private final UserRepository userRepository; // Use final with @RequiredArgsConstructor

    @Transactional // Good practice for methods that modify data
    public UserResponse register(RegisterRequest request) {
        // Log the incoming request for debugging
        log.info("Register request received for email: {}, keycloakId: {}", request.getEmail(), request.getKeycloakId());

        // Step 1: Check if a user already exists with this Keycloak ID.
        // This is the most reliable way to find an already synced user.
        if (request.getKeycloakId() != null) { // Ensure keycloakId is present in request
            Optional<User> userByKeycloakIdOpt = userRepository.findByKeycloakId(request.getKeycloakId());
            if (userByKeycloakIdOpt.isPresent()) {
                User existingUser = userByKeycloakIdOpt.get();
                log.info("User found by Keycloak ID: {}. Returning existing user.", request.getKeycloakId());
                // Optionally: Update email/firstName/lastName if they differ from Keycloak claims, if desired.
                // For now, just return the existing user.
                return mapToUserResponse(existingUser);
            }
        }

        // Step 2: If no user found by Keycloak ID, check by email.
        // This handles cases where the user might exist locally (e.g., pre-Keycloak)
        // but isn't yet linked, OR it's a completely new user.
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
                if (existingUserByEmail.getFirstName() == null || existingUserByEmail.getFirstName().isEmpty()) {
                    existingUserByEmail.setFirstName(request.getFirstName());
                }
                if (existingUserByEmail.getLastName() == null || existingUserByEmail.getLastName().isEmpty()) {
                    existingUserByEmail.setLastName(request.getLastName());
                }
                User updatedUser = userRepository.save(existingUserByEmail);
                return mapToUserResponse(updatedUser);
            } else if (!existingUserByEmail.getKeycloakId().equals(request.getKeycloakId())) {
                // This email is ALREADY linked to a DIFFERENT Keycloak ID. This is a conflict.
                log.warn("Conflict: Email {} is already linked to Keycloak ID {}, but current token has Keycloak ID {}.",
                        request.getEmail(), existingUserByEmail.getKeycloakId(), request.getKeycloakId());
                // Decide how to handle this: throw error, log and ignore, etc.
                // For now, we'll just return the user as they are (linked to the OLD Keycloak ID).
                // Or you might throw an exception:
                // throw new IllegalStateException("Email " + request.getEmail() + " is already associated with a different user account.");
                return mapToUserResponse(existingUserByEmail); // Return existing user without changing Keycloak ID
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
        newUser.setPassword(request.getPassword()); // Storing dummy password as is (plain text - for now)
        newUser.setKeycloakId(request.getKeycloakId());
        newUser.setFirstName(request.getFirstName());
        newUser.setLastName(request.getLastName());
        // Set default role if applicable
        // newUser.setRole(UserRole.USER);

        User savedUser = userRepository.save(newUser);
        log.info("New user created with ID: {} and Keycloak ID: {}", savedUser.getId(), savedUser.getKeycloakId());
        return mapToUserResponse(savedUser);
    }

    // Helper method to map User entity to UserResponse DTO
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
    public UserResponse getUserProfile(String userId) { // This userId is internal DB ID
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with internal id: " + userId)); // TODO: Custom exception
        return mapToUserResponse(user);
    }

    // This method is called with Keycloak ID from the gateway
    @Transactional(readOnly = true)
    public Boolean existByUserId(String keycloakId) {
        log.info("UserService (USER-SERVICE): Checking existence by Keycloak ID: {}", keycloakId);
        return userRepository.existsByKeycloakId(keycloakId);
    }
}