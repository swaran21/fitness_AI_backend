package com.fitness.gateway;

import com.fitness.gateway.user.RegisterRequest;
import com.fitness.gateway.user.UserService; // Assuming this is your WebClient based service in gateway
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain; // Correct import for Gateway
import org.springframework.cloud.gateway.filter.GlobalFilter;     // Correct import for Gateway
import org.springframework.core.Ordered;                            // For filter order
import org.springframework.http.HttpHeaders;                     // For HttpHeaders.AUTHORIZATION
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.text.ParseException; // For SignedJWT.parse

@Component
@Slf4j
@RequiredArgsConstructor
public class KeyCloakUserSyncFilter implements GlobalFilter, Ordered { // Implement GlobalFilter & Ordered

    private final UserService gatewayUserService; // Renamed for clarity to distinguish from backend UserService

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        log.debug("GATEWAY KeyCloakUserSyncFilter: Intercepting request.");
        String authHeader = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        // If no Authorization header or not a Bearer token, proceed without JIT sync.
        // Spring Security later will handle if the endpoint requires authentication.
        if (authHeader == null || !authHeader.toLowerCase().startsWith("bearer ")) {
            log.debug("GATEWAY KeyCloakUserSyncFilter: No Bearer token found. Passing request through.");
            return chain.filter(exchange);
        }

        // Attempt to get Keycloak User ID from the token for JIT provisioning and header enrichment.
        RegisterRequest registerRequestDetails = extractUserDetailsFromToken(authHeader);

        if (registerRequestDetails == null || registerRequestDetails.getKeycloakId() == null || registerRequestDetails.getKeycloakId().trim().isEmpty()) {
            log.warn("GATEWAY KeyCloakUserSyncFilter: Could not extract valid Keycloak ID ('sub') from token. Passing request through without X-User-ID or JIT sync.");
            // Still pass the request; Spring Security will reject if token is truly invalid for a protected route.
            return chain.filter(exchange);
        }

        String keycloakIdFromToken = registerRequestDetails.getKeycloakId();
        log.info("GATEWAY KeyCloakUserSyncFilter: Keycloak ID [{}] extracted from token.", keycloakIdFromToken);

        // --- JIT User Provisioning Logic ---
        Mono<Void> jitProvisioningMono = gatewayUserService.validateUser(keycloakIdFromToken) // Calls USER-SERVICE
                .flatMap(userExistsInLocalDb -> {
                    if (!userExistsInLocalDb) {
                        log.info("GATEWAY KeyCloakUserSyncFilter: User with Keycloak ID [{}] does not exist locally. Initiating JIT registration.", keycloakIdFromToken);
                        return gatewayUserService.registerUser(registerRequestDetails) // Calls USER-SERVICE
                                .doOnSuccess(response -> log.info("GATEWAY KeyCloakUserSyncFilter: JIT registration successful for Keycloak ID [{}], local user ID [{}]", keycloakIdFromToken, response.getId()))
                                .then(Mono.empty()); // Ensure the type matches for flatMap chaining
                    } else {
                        log.info("GATEWAY KeyCloakUserSyncFilter: User with Keycloak ID [{}] already exists locally. Skipping JIT registration.", keycloakIdFromToken);
                        return Mono.empty(); // User exists, no registration action needed
                    }
                })
                .onErrorResume(error -> {
                    // Log the JIT provisioning error but allow the original request to proceed.
                    // The main API call might still succeed if JIT provisioning is not strictly critical for it.
                    log.error("GATEWAY KeyCloakUserSyncFilter: Error during JIT user provisioning for Keycloak ID [{}]: {}. Request will proceed.", keycloakIdFromToken, error.getMessage(), error);
                    return Mono.empty(); // Proceed even if JIT fails. Consider implications.
                }).then();

        // After JIT provisioning attempt (success, skip, or error), mutate request to add X-User-ID and pass down the chain.
        return jitProvisioningMono.then(Mono.defer(() -> {
            log.debug("GATEWAY KeyCloakUserSyncFilter: Mutating request to add/overwrite X-User-ID header with [{}].", keycloakIdFromToken);
            ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                    .header("X-User-ID", keycloakIdFromToken) // Always set based on token
                    .build();
            ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();
            return chain.filter(mutatedExchange);
        }));
    }

    private RegisterRequest extractUserDetailsFromToken(String authHeaderValue) {
        try {
            // "Bearer ".length() is 7
            String token = authHeaderValue.substring(7).trim();
            SignedJWT signedJWT = SignedJWT.parse(token);
            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();

            // Log all claims for debugging if needed, but be careful with sensitive data in production logs
            log.trace("GATEWAY KeyCloakUserSyncFilter: Decoded JWT claims: {}", claims.toJSONObject());

            String keycloakId = claims.getStringClaim("sub");
            if (keycloakId == null || keycloakId.trim().isEmpty()) {
                log.warn("GATEWAY KeyCloakUserSyncFilter: 'sub' claim (Keycloak ID) is missing or empty in token.");
                return null;
            }

            RegisterRequest userDetails = new RegisterRequest();
            userDetails.setKeycloakId(keycloakId);
            userDetails.setEmail(claims.getStringClaim("email"));
            userDetails.setFirstName(claims.getStringClaim("given_name"));
            userDetails.setLastName(claims.getStringClaim("family_name"));
            userDetails.setPassword("dummy-password-keycloak-sync"); // Dummy password for JIT

            log.debug("GATEWAY KeyCloakUserSyncFilter: Extracted user details from token: Email [{}], KeycloakID [{}]", userDetails.getEmail(), userDetails.getKeycloakId());
            return userDetails;

        } catch (ParseException e) {
            log.error("GATEWAY KeyCloakUserSyncFilter: Failed to parse JWT token: {}", e.getMessage());
        } catch (Exception e) { // Catch broader exceptions for unexpected issues during claim extraction
            log.error("GATEWAY KeyCloakUserSyncFilter: Unexpected error extracting user details from token: {}", e.getMessage(), e);
        }
        return null; // Return null if any error occurs
    }

    @Override
    public int getOrder() {
        // Run this filter before Spring Security's main authentication filters, but after any metrics/logging filters.
        // A value slightly higher than SecurityWebFiltersOrder.AUTHENTICATION.
        // Or simply a low value like -100 to ensure it runs early for header modification.
        // For GlobalFilters, Ordered.HIGHEST_PRECEDENCE + N can be used. Let's try a common value.
        return -100; // Ensure it runs fairly early
    }
}