package com.wallet.digital_wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.digital_wallet.dto.LoginRequest;
import com.wallet.digital_wallet.repository.UserRepository;
import com.wallet.digital_wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for TransactionController.
 *
 * @DirtiesContext: forces a fresh Spring application context (and H2 schema)
 * for this test class. Without it, if AuthControllerIntegrationTest already
 * ran in the same JVM session and the H2 schema was dropped by its context
 * closing, this class would fail with "Table not found" errors on startup.
 * Cost: ~3s extra startup time. Worth it for test isolation.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@DisplayName("TransactionController Integration Tests")
class TransactionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserRepository userRepository;

    private String user1Token;
    private Long user1WalletId;

    @BeforeEach
    void obtainTokenAndWalletId() throws Exception {
        // Step 1: Login to get a fresh JWT token
        LoginRequest req = new LoginRequest();
        req.setUsername("user1");
        req.setPassword("user123");

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        user1Token = objectMapper.readTree(response)
                .path("data").path("token").asText();

        // Step 2: Look up user1's wallet ID by username, NOT by hardcoded id.
        // DataInitializer creates admin first (id=1) then user1 (id=2).
        // Hardcoding findByUser_Id(1L) would return admin's wallet, not user1's.
        user1WalletId = userRepository.findByUsername("user1")
                .flatMap(user -> walletRepository.findByUser_Id(user.getId()))
                .map(wallet -> wallet.getId())
                .orElseThrow(() -> new RuntimeException(
                        "user1's wallet not found — DataInitializer may not have run"));
    }

    // ── Authentication enforcement ─────────────────────────────────────────

    @Test
    @DisplayName("POST /credit: no token → 401")
    void credit_withoutToken_returns401() throws Exception {
        mockMvc.perform(post("/api/transactions/credit/" + user1WalletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 100}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /credit: invalid Bearer token → 401")
    void credit_withInvalidToken_returns401() throws Exception {
        mockMvc.perform(post("/api/transactions/credit/" + user1WalletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer this.is.not.a.real.jwt")
                        .content("{\"amount\": 100}"))
                .andExpect(status().isUnauthorized());
    }

    // ── Validation enforcement ─────────────────────────────────────────────

    @Test
    @DisplayName("POST /credit: null amount → 400 Validation Failed")
    void credit_nullAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/transactions/credit/" + user1WalletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + user1Token)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.amount").exists());
    }

    @Test
    @DisplayName("POST /credit: negative amount → 400")
    void credit_negativeAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/transactions/credit/" + user1WalletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + user1Token)
                        .content("{\"amount\": -50.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount", containsString("0.01")));
    }

    @Test
    @DisplayName("POST /credit: zero amount → 400")
    void credit_zeroAmount_returns400() throws Exception {
        mockMvc.perform(post("/api/transactions/credit/" + user1WalletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + user1Token)
                        .content("{\"amount\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /transfer: missing receiverWalletNumber → 400")
    void transfer_missingReceiverWallet_returns400() throws Exception {
        mockMvc.perform(post("/api/transactions/transfer/" + user1WalletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + user1Token)
                        .content("{\"amount\": 100.00}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.receiverWalletNumber").exists());
    }

    @Test
    @DisplayName("POST /transfer: bad wallet number format → 400")
    void transfer_invalidWalletNumberFormat_returns400() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "amount", 100.00,
                "receiverWalletNumber", "not-a-valid-format"
        ));

        mockMvc.perform(post("/api/transactions/transfer/" + user1WalletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + user1Token)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.receiverWalletNumber").exists());
    }

    // ── IDOR enforcement ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /history/{id}: accessing another user's wallet → 403")
    void history_anotherUsersWallet_returns403() throws Exception {
        // Find admin's wallet ID — user1 should NOT be able to view it
        Long adminWalletId = userRepository.findByUsername("admin")
                .flatMap(u -> walletRepository.findByUser_Id(u.getId()))
                .map(w -> w.getId())
                .orElse(999L);

        mockMvc.perform(get("/api/transactions/history/" + adminWalletId)
                        .header("Authorization", "Bearer " + user1Token))
                .andExpect(status().isForbidden());
    }

    // ── Happy path ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /credit: valid request + token → 200 with transaction")
    void credit_validRequest_returns200() throws Exception {
        mockMvc.perform(post("/api/transactions/credit/" + user1WalletId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", "Bearer " + user1Token)
                        .content("{\"amount\": 50.00, \"description\": \"Test top-up\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.type").value("CREDIT"))
                .andExpect(jsonPath("$.data.amount").value(50.00));
    }
}