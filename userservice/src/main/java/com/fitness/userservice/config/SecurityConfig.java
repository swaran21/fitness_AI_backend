// package com.fitness.userservice.config;
package com.fitness.userservice.config; // Or your config package

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
// @EnableWebSecurity // Might be needed depending on Spring Boot version and auto-configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // For a resource server that relies on API Gateway for primary auth,
        // you might disable CSRF and permit all requests if tokens are validated at gateway.
        // Or, configure it as an OAuth2 resource server too if it needs to validate tokens itself.
        // For now, to just enable PasswordEncoder without full security setup blocking requests:
        http
                .csrf(AbstractHttpConfigurer::disable) // Disable CSRF if API is stateless
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().permitAll() // Permit all FOR NOW, just to get PasswordEncoder. Refine later.
                );
        return http.build();
    }
}