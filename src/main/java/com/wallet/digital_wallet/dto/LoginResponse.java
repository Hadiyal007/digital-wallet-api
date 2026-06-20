package com.wallet.digital_wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private String tokenType; // always "Bearer" - tells the client how to use it
    private String username;
    private String role;
    private long expiresInMs;
}