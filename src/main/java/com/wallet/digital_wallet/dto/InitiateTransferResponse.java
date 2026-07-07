package com.wallet.digital_wallet.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class InitiateTransferResponse {
    private String referenceId;   // pass this back to /transfer/verify
    private long expiresInMs;     // how long the OTP is valid for
}
