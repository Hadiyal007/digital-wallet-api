package com.wallet.digital_wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class TokenRefreshResponse {
    private String accessToken;
    private String refreshToken;   // rotated - the old one no longer works
    private String tokenType;      // always "Bearer"
    private long expiresInMs;      // access token lifetime, for the client
}
