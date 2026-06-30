package com.wallet.digital_wallet.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/**
 * Public health check endpoint used by Render to verify the app is alive.
 *
 * Why this is needed:
 * Render pings a configured health check path after deploying your JAR.
 * If that path requires authentication (like every other endpoint in this
 * app does), Render's check gets a 401 and marks the deploy as failed,
 * even though the app is actually running fine.
 *
 * This endpoint is explicitly permitted in SecurityConfig (no JWT required).
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "digital-wallet-api"
        ));
    }
}