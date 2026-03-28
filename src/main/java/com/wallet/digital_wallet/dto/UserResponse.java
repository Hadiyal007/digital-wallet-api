package com.wallet.digital_wallet.dto;

import com.wallet.digital_wallet.entity.User;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class UserResponse {

    private Long id;
    private String fullName;
    private String username;
    private String email;
    private String role;
    private Long walletId;
    private String walletNumber;
    private BigDecimal balance;
    private String walletStatus;

    public static UserResponse from(User user) {
        UserResponse res = new UserResponse();
        res.id = user.getId();
        res.fullName = user.getFullName();
        res.username = user.getUsername();
        res.email = user.getEmail();
        res.role = user.getRole().name();

        if (user.getWallet() != null) {
            res.walletId = user.getWallet().getId();
            res.walletNumber = user.getWallet().getWalletNumber();
            res.balance = user.getWallet().getBalance();
            res.walletStatus = user.getWallet().getStatus().name();
        }
        return res;
    }
}