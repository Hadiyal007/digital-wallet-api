package com.wallet.digital_wallet.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferRequest extends TransactionRequest {

    @NotBlank(message = "Receiver wallet number is required for transfers")
    @Pattern(
            regexp = "^WALL-[A-Z0-9]{4,8}$",
            message = "Invalid wallet number format. Expected format: WALL-XXXXXXXX"
    )
    private String receiverWalletNumber;
}