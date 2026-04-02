package com.wallet.digital_wallet.dto;

import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateUserRequest {

    private String fullName;   // optional — only update if provided
    private String email;      // optional
    private String password;   // optional — will be re-encoded
}