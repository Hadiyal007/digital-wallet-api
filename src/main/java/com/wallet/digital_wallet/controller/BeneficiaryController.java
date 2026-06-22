package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.dto.BeneficiaryRequest;
import com.wallet.digital_wallet.entity.Beneficiary;
import com.wallet.digital_wallet.entity.User;
import com.wallet.digital_wallet.exception.UnauthorizedAccessException;
import com.wallet.digital_wallet.service.BeneficiaryService;
import com.wallet.digital_wallet.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/beneficiaries")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;
    private final UserService userService;

    /**
     * Resolves the real, authenticated user and verifies that the userId
     * supplied in the URL actually matches them (or that the caller is an
     * admin). This is the core IDOR fix: we never trust the path userId
     * alone - we cross-check it against the JWT-derived identity.
     */
    private User resolveAuthorizedUser(Long pathUserId, Authentication authentication) {
        User authenticatedUser = userService.getUserByUsername(authentication.getName());

        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!authenticatedUser.getId().equals(pathUserId) && !isAdmin) {
            throw new UnauthorizedAccessException(
                    "Access denied: you can only manage your own beneficiaries");
        }

        // If admin is managing someone else's beneficiaries, fetch that user;
        // otherwise it's just the authenticated user themselves.
        return authenticatedUser.getId().equals(pathUserId)
                ? authenticatedUser
                : userService.getUserById(pathUserId);
    }

    // POST /api/beneficiaries/{userId}
    @PostMapping("/{userId}")
    public ResponseEntity<ApiResponse<Beneficiary>> add(
            @PathVariable Long userId,
            @Valid @RequestBody BeneficiaryRequest request,
            Authentication authentication) {

        User user = resolveAuthorizedUser(userId, authentication);
        Beneficiary b = beneficiaryService.addBeneficiary(
                user,
                request.getWalletNumber(),
                request.getBeneficiaryName(),
                request.getNickname());
        return ResponseEntity.ok(ApiResponse.success("Beneficiary added", b));
    }

    // GET /api/beneficiaries/{userId}
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<List<Beneficiary>>> list(
            @PathVariable Long userId,
            Authentication authentication) {

        User user = resolveAuthorizedUser(userId, authentication);
        List<Beneficiary> list = beneficiaryService.getBeneficiaries(user);
        return ResponseEntity.ok(ApiResponse.success("Beneficiaries", list));
    }

    // DELETE /api/beneficiaries/{userId}/{beneficiaryId}
    @DeleteMapping("/{userId}/{beneficiaryId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long userId,
            @PathVariable Long beneficiaryId,
            Authentication authentication) {

        User user = resolveAuthorizedUser(userId, authentication);
        beneficiaryService.deleteBeneficiary(beneficiaryId, user);
        return ResponseEntity.ok(ApiResponse.success("Beneficiary removed", null));
    }
}