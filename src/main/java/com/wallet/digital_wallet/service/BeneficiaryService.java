package com.wallet.digital_wallet.service;

import com.wallet.digital_wallet.entity.Beneficiary;
import com.wallet.digital_wallet.entity.User;
import com.wallet.digital_wallet.exception.DuplicateResourceException;
import com.wallet.digital_wallet.exception.ResourceNotFoundException;
import com.wallet.digital_wallet.repository.BeneficiaryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BeneficiaryService {

    private final BeneficiaryRepository beneficiaryRepository;
    
    public BeneficiaryService(BeneficiaryRepository beneficiaryRepository) {
        this.beneficiaryRepository = beneficiaryRepository;
    }

    public Beneficiary addBeneficiary(User user, String walletNumber,
                                      String name, String nickname) {
        if (walletNumber == null || walletNumber.isBlank()) {
            throw new IllegalArgumentException("Beneficiary wallet number is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Beneficiary name is required");
        }

        // Don't allow duplicate beneficiary for same user
        beneficiaryRepository
                .findByUserAndBeneficiaryWalletNumber(user, walletNumber)
                .ifPresent(b -> { throw new DuplicateResourceException(
                        "Beneficiary already exists with wallet: " + walletNumber); });

        Beneficiary b = new Beneficiary(
                user,
                walletNumber,
                name,
                nickname,
                LocalDateTime.now()
        );

        return beneficiaryRepository.save(b);
    }

    public List<Beneficiary> getBeneficiaries(User user) {
        return beneficiaryRepository.findByUser(user);
    }

    public void deleteBeneficiary(Long id, User user) {
        Beneficiary b = beneficiaryRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Beneficiary not found for this user: " + id));
        beneficiaryRepository.delete(b);
    }
}
