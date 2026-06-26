package com.wallet.digital_wallet.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateUserRequest {

    // All fields optional — constraints only fire if the field is non-null.
    // @Size on a null String is silently skipped by Bean Validation.
    // @Email on a null String is silently skipped by Bean Validation.

    @Size(min = 2, max = 100, message = "Full name must be between 2 and 100 characters")
    private String fullName;

    @Email(message = "Please provide a valid email address")
    @Size(max = 100, message = "Email must not exceed 100 characters")
    private String email;

    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters")
    private String password;
}