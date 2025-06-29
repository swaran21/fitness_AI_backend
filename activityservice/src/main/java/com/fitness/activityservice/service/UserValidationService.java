// From activity-service: src/main/java/com/fitness/activityservice/service/UserValidationService.java

package com.fitness.activityservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserValidationService {

    private final WebClient userServiceWebClient;

    public boolean validateUser(String userId){
        log.info("Calling User Service to ensure user exists for userId: {}", userId);
        try {
            // --- UPDATED API CALL ---
            // We now call the new POST endpoint. We don't need the response body,
            // we just need the call to complete without an error (i.e., return a 2xx status).
            userServiceWebClient.post()
                    .uri("/api/users/{userId}/ensure-exists", userId)
                    .retrieve()
                    .toBodilessEntity() // We don't care about the body, just the status code
                    .block(); // Block and wait for completion. Throws WebClientResponseException on 4xx/5xx errors.

            // If we reach here without an exception, the user exists or was just created.
            log.info("User successfully validated (existed or was created): {}", userId);
            return true;
        }
        catch (Exception e){
            // Any exception here (4xx, 5xx, timeout) means the validation failed.
            log.error("Failed to validate user with user-service for userId: {}", userId, e);
            throw new RuntimeException("User could not be validated: "+userId, e);
        }
    }
}