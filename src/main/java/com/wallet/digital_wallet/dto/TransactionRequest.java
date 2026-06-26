package com.wallet.digital_wallet.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Getter @Setter
public class TransactionRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @Digits(integer = 10, fraction = 2,
            message = "Amount must have at most 10 integer digits and 2 decimal places")
    private BigDecimal amount;

    private String description;

    // Nullable here — only TransferRequest enforces @NotBlank on this field
    private String receiverWalletNumber;
}