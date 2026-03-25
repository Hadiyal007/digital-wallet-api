package com.wallet.digital_wallet.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class BeneficiaryRequest {

    @NotBlank(message = "Wallet number is required")
    private String walletNumber;

    @NotBlank(message = "Beneficiary name is required")
    private String beneficiaryName;

    private String nickname;
}