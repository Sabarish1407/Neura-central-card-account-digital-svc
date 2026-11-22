package com.nuera.banking.card.service.service;

import com.nuera.banking.card.model.dto.AccountCreateRequest;
import com.nuera.banking.card.model.dto.AccountDepositRequest;
import com.nuera.banking.card.model.dto.AccountResponse;
import com.nuera.banking.card.model.enums.AccountStatus;
import com.nuera.banking.card.service.entity.AccountEntity;
import com.nuera.banking.card.service.exception.BankingException;
import com.nuera.banking.card.service.exception.ResourceNotFoundException;
import com.nuera.banking.card.service.repository.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final SecureRandom random = new SecureRandom();

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public AccountResponse createAccount(AccountCreateRequest request) {
        String accountId = "acc-" + UUID.randomUUID().toString().substring(0, 8);
        String accountNumber = "10" + String.format("%08d", random.nextInt(100_000_000));
        BigDecimal initial = request.getInitialDeposit() != null ? request.getInitialDeposit() : BigDecimal.ZERO;

        AccountEntity entity = new AccountEntity(
                accountId,
                accountNumber,
                request.getCustomerId(),
                request.getCustomerName(),
                request.getAccountType(),
                initial,
                request.getCurrency(),
                AccountStatus.ACTIVE
        );

        AccountEntity saved = accountRepository.save(entity);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountById(String accountId) {
        AccountEntity entity = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));
        return mapToResponse(entity);
    }

    @Transactional
    public AccountResponse depositFunds(String accountId, AccountDepositRequest request) {
        AccountEntity entity = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + accountId));

        if (entity.getStatus() != AccountStatus.ACTIVE) {
            throw new BankingException("Cannot deposit into an inactive account", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        entity.setBalance(entity.getBalance().add(request.getAmount()));
        entity.setAvailableBalance(entity.getAvailableBalance().add(request.getAmount()));
        entity.setUpdatedAt(Instant.now());

        AccountEntity updated = accountRepository.save(entity);
        return mapToResponse(updated);
    }

    public AccountResponse mapToResponse(AccountEntity entity) {
        AccountResponse res = new AccountResponse();
        res.setAccountId(entity.getAccountId());
        res.setAccountNumber(entity.getAccountNumber());
        res.setCustomerId(entity.getCustomerId());
        res.setCustomerName(entity.getCustomerName());
        res.setAccountType(entity.getAccountType());
        res.setBalance(entity.getBalance());
        res.setAvailableBalance(entity.getAvailableBalance());
        res.setCurrency(entity.getCurrency());
        res.setStatus(entity.getStatus());
        res.setCreatedAt(entity.getCreatedAt());
        return res;
    }
}
