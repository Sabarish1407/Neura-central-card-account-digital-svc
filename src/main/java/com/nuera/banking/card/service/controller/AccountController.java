package com.nuera.banking.card.service.controller;

import com.nuera.banking.card.model.dto.AccountCreateRequest;
import com.nuera.banking.card.model.dto.AccountDepositRequest;
import com.nuera.banking.card.model.dto.AccountResponse;
import com.nuera.banking.card.service.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts API", description = "Core digital banking account operations")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @Operation(summary = "Open a new digital banking account")
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody AccountCreateRequest request) {
        AccountResponse response = accountService.createAccount(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{accountId}")
    @Operation(summary = "Get account details by ID")
    public ResponseEntity<AccountResponse> getAccountById(@PathVariable String accountId) {
        return ResponseEntity.ok(accountService.getAccountById(accountId));
    }

    @PostMapping("/{accountId}/deposit")
    @Operation(summary = "Deposit funds into an account")
    public ResponseEntity<AccountResponse> depositFunds(@PathVariable String accountId,
                                                        @Valid @RequestBody AccountDepositRequest request) {
        return ResponseEntity.ok(accountService.depositFunds(accountId, request));
    }
}
