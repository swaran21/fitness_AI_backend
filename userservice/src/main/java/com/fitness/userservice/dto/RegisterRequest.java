package com.fitness.userservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Password cannot be blank")
    @Size(min=6,message = "Password must have at least 6 characters")
    private String password;
    private String firstName;
    private String lastName;

}
