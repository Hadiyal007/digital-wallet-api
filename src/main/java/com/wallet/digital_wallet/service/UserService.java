package com.wallet.digital_wallet.service;

import com.wallet.digital_wallet.dto.RegisterRequest;
import com.wallet.digital_wallet.dto.UpdateUserRequest;
import com.wallet.digital_wallet.entity.User;
import com.wallet.digital_wallet.entity.User.Role;
import com.wallet.digital_wallet.exception.DuplicateResourceException;
import com.wallet.digital_wallet.exception.ResourceNotFoundException;
import com.wallet.digital_wallet.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final WalletService walletService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public User registerUser(RegisterRequest request) {

        // Check duplicates
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException(
                    "Username already taken: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException(
                    "Email already registered: " + request.getEmail());
        }

        // Build and save user
        User user = User.builder()
                .fullName(request.getFullName())
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword())) // BCrypt
                .role(Role.ROLE_USER)
                .build();

        User savedUser = userRepository.save(user);

        // Auto-create wallet for this user
        walletService.createWallet(savedUser);

        return savedUser;
    }

    public User getUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + id));
    }

    public User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found: " + username));
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @Transactional
    public User updateUser(Long id, UpdateUserRequest request, String authenticatedUsername) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        // Only the user themselves OR admin can update
        boolean isAdmin = userRepository.findByUsername(authenticatedUsername)
                .map(u -> u.getRole() == User.Role.ROLE_ADMIN)
                .orElse(false);

        if (!user.getUsername().equals(authenticatedUsername) && !isAdmin) {
            throw new RuntimeException("Access denied: you can only update your own profile");
        }

        // Only update fields that are actually provided
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
        }

        if (request.getEmail() != null && !request.getEmail().isBlank()) {
            // Check email not taken by someone else
            userRepository.findByEmail(request.getEmail()).ifPresent(existing -> {
                if (!existing.getId().equals(id)) {
                    throw new DuplicateResourceException("Email already in use: " + request.getEmail());
                }
            });
            user.setEmail(request.getEmail());
        }

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id, String authenticatedUsername) {

        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

        // Only admin can delete users
        User requester = userRepository.findByUsername(authenticatedUsername)
                .orElseThrow(() -> new ResourceNotFoundException("Requester not found"));

        if (requester.getRole() != User.Role.ROLE_ADMIN) {
            throw new RuntimeException("Access denied: only admin can delete users");
        }

        // Prevent admin from deleting themselves
        if (user.getUsername().equals(authenticatedUsername)) {
            throw new RuntimeException("Admin cannot delete their own account");
        }

        userRepository.delete(user);
    }
}