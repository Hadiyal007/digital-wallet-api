package com.wallet.digital_wallet.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Entity
@Table(name = "users")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Full name is required")
    private String fullName;

    @NotBlank(message = "Username is required")
    @Column(unique = true, nullable = false)
    private String username;

    @Email(message = "Enter a valid email")
    @Column(unique = true, nullable = false)
    private String email;

    @NotBlank(message = "Password is required")
    private String password;   // stored encoded (BCrypt) — never plain text

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    // One user has exactly one wallet
    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private Wallet wallet;

    public enum Role {
        ROLE_ADMIN,
        ROLE_USER
    }
}