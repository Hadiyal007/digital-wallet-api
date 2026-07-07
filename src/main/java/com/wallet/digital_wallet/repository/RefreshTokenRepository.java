package com.wallet.digital_wallet.repository;

import com.wallet.digital_wallet.entity.RefreshToken;
import com.wallet.digital_wallet.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    // Used on login and on rotation: a user should only ever have ONE valid
    // refresh token at a time. Deleting old ones before issuing a new one
    // keeps the table small and means an old device's refresh token stops
    // working the moment you log in again elsewhere - simple, explainable
    // behaviour for a student project (real systems often allow multiple
    // active sessions per user, one refresh token each, which is a natural
    // "future enhancement" to mention in interviews).
    void deleteByUser(User user);
}
