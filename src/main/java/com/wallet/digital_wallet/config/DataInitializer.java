package com.wallet.digital_wallet.config;

import com.wallet.digital_wallet.entity.User;
import com.wallet.digital_wallet.entity.User.Role;
import com.wallet.digital_wallet.entity.Wallet;
import com.wallet.digital_wallet.entity.Wallet.WalletStatus;
import com.wallet.digital_wallet.repository.UserRepository;
import com.wallet.digital_wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {

        // Only create if admin doesn't exist yet
        if (userRepository.existsByUsername("admin")) return;

        // Create ADMIN user
        User admin = User.builder()
                .fullName("System Admin")
                .username("admin")
                .email("admin@wallet.com")
                .password(passwordEncoder.encode("admin123"))
                .role(Role.ROLE_ADMIN)
                .build();
        User savedAdmin = userRepository.save(admin);

        // Create admin wallet
        walletRepository.save(Wallet.builder()
                .walletNumber("WALL-ADMIN-001")
                .balance(BigDecimal.ZERO)
                .status(WalletStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .user(savedAdmin)
                .build());

        // Create a demo USER
        User demoUser = User.builder()
                .fullName("Demo User")
                .username("user1")
                .email("user1@wallet.com")
                .password(passwordEncoder.encode("user123"))
                .role(Role.ROLE_USER)
                .build();
        User savedUser = userRepository.save(demoUser);

        // Create demo user wallet
        walletRepository.save(Wallet.builder()
                .walletNumber("WALL-USER-001")
                .balance(new BigDecimal("10000.00"))
                .status(WalletStatus.ACTIVE)
                .createdAt(LocalDateTime.now())
                .user(savedUser)
                .build());

        System.out.println("=== Demo data loaded ===");
        System.out.println("ADMIN  → username: admin    password: admin123");
        System.out.println("USER   → username: user1    password: user123");
        System.out.println("========================");
    }
}