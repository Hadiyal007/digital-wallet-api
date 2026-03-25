package com.wallet.digital_wallet.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "beneficiaries")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Beneficiary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The user who SAVED this beneficiary
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // The wallet number of the person to send money to
    @NotBlank(message = "Beneficiary wallet number is required")
    @Column(nullable = false)
    private String beneficiaryWalletNumber;

    @NotBlank(message = "Beneficiary name is required")
    private String beneficiaryName;

    private String nickname;   // e.g. "Mom", "Landlord"

    private LocalDateTime addedAt;

    public Beneficiary(User user, String beneficiaryWalletNumber,
                       String beneficiaryName, String nickname, LocalDateTime addedAt) {
        this.user = user;
        this.beneficiaryWalletNumber = beneficiaryWalletNumber;
        this.beneficiaryName = beneficiaryName;
        this.nickname = nickname;
        this.addedAt = addedAt;
    }
}