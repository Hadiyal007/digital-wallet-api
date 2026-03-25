package com.wallet.digital_wallet.controller;



import com.wallet.digital_wallet.entity.User;
import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;

    // GET /api/auth/me
    // Shows who is currently logged in — great for Postman demo
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<User>> getCurrentUser(
            Authentication authentication) {

        String username = authentication.getName();
        User user = userService.getUserByUsername(username);
        return ResponseEntity.ok(
                ApiResponse.success("Logged in as: " + username, user));
    }
}