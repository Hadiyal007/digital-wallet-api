package com.wallet.digital_wallet.controller;

import com.wallet.digital_wallet.dto.ApiResponse;
import com.wallet.digital_wallet.dto.BeneficiaryRequest;
import com.wallet.digital_wallet.entity.Beneficiary;
import com.wallet.digital_wallet.entity.User;
import com.wallet.digital_wallet.service.BeneficiaryService;
import com.wallet.digital_wallet.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/beneficiaries")
@RequiredArgsConstructor
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;
    private final UserService userService;

    // POST /api/beneficiaries/{userId}
    @PostMapping("/{userId}")
    public ResponseEntity<ApiResponse<Beneficiary>> add(
            @PathVariable Long userId,
            @Valid @RequestBody BeneficiaryRequest request) {

        User user = userService.getUserById(userId);
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
            @PathVariable Long userId) {

        User user = userService.getUserById(userId);
        List<Beneficiary> list = beneficiaryService.getBeneficiaries(user);
        return ResponseEntity.ok(ApiResponse.success("Beneficiaries", list));
    }

    // DELETE /api/beneficiaries/{userId}/{beneficiaryId}
    @DeleteMapping("/{userId}/{beneficiaryId}")
    public ResponseEntity<ApiResponse<Void>> delete(
            @PathVariable Long userId,
            @PathVariable Long beneficiaryId) {

        User user = userService.getUserById(userId);
        beneficiaryService.deleteBeneficiary(beneficiaryId, user);
        return ResponseEntity.ok(ApiResponse.success("Beneficiary removed", null));
    }
}