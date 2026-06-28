package com.wallet.digital_wallet.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallet.digital_wallet.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for AuthController.
 *
 * @SpringBootTest: starts the full Spring context using H2 in-memory DB.
 * @AutoConfigureMockMvc: wires MockMvc through the full filter chain.
 * @ActiveProfiles("test"): activates application-test.properties.
 * @DirtiesContext: forces a fresh context + H2 schema for this class,
 *   preventing schema-drop collisions when multiple @SpringBootTest classes
 *   run in the same JVM session.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@DisplayName("AuthController Integration Tests")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper; // for serialising request DTOs to JSON

    // ── Login endpoint ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/auth/login: valid credentials return 200 with JWT token")
    void login_validCredentials_returns200WithToken() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("user1");
        request.setPassword("user123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                // data.token must exist and not be empty
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.token", not(emptyString())))
                // tokenType must be "Bearer"
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                // username must echo back
                .andExpect(jsonPath("$.data.username").value("user1"))
                // role must be ROLE_USER
                .andExpect(jsonPath("$.data.role").value("ROLE_USER"));
    }

    @Test
    @DisplayName("POST /api/auth/login: admin credentials return ROLE_ADMIN")
    void login_adminCredentials_returnsAdminRole() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ROLE_ADMIN"));
    }

    @Test
    @DisplayName("POST /api/auth/login: wrong password returns 401")
    void login_wrongPassword_returns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("user1");
        request.setPassword("wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                // Generic message — should NOT reveal whether user exists
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @DisplayName("POST /api/auth/login: non-existent user returns 401 (not 404)")
    void login_nonExistentUser_returns401NotLeakingExistence() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setUsername("doesnotexist");
        request.setPassword("anypassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                // IMPORTANT: Same message as wrong password — prevents username enumeration.
                // A 404 "user not found" would tell the attacker the username doesn't exist.
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    @DisplayName("POST /api/auth/login: empty body returns 400 with field errors")
    void login_emptyBody_returns400WithValidationErrors() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @DisplayName("GET /api/auth/me: without token returns 401")
    void me_withoutToken_returns401() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/auth/me: with valid token returns 200 with user profile")
    void me_withValidToken_returns200() throws Exception {
        // Step 1: login to get a token
        LoginRequest loginReq = new LoginRequest();
        loginReq.setUsername("user1");
        loginReq.setPassword("user123");

        String responseBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Step 2: extract token from JSON response
        String token = objectMapper.readTree(responseBody)
                .path("data").path("token").asText();

        // Step 3: call /me with the token
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("user1"));
    }
}