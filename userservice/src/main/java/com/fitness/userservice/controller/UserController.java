package com.fitness.userservice.controller;

import com.fitness.userservice.dto.RegisterRequest;
import com.fitness.userservice.dto.UserResponse;
import com.fitness.userservice.service.UserService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@AllArgsConstructor
public class UserController {

    private UserService userService;

    @GetMapping("/{userId}")
    public ResponseEntity<UserResponse> getUserProfile(@PathVariable String userId) {
        return ResponseEntity.ok(userService.getUserProfile(userId));
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(userService.register(request));
    }

    @PostMapping("/{authProviderId}/ensure-exists")
    public ResponseEntity<UserResponse> ensureUserExists(@PathVariable String authProviderId) {
        UserResponse userResponse = userService.ensureUserExists(authProviderId);
        // We can return 201 Created if the user was new, or 200 OK if they existed.
        // For simplicity, returning 200 OK in both cases is also fine.
        return ResponseEntity.status(HttpStatus.OK).body(userResponse);
    }


}
