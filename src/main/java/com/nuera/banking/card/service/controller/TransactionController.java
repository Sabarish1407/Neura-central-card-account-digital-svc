package com.nuera.banking.card.service.controller;

import com.nuera.banking.card.model.dto.TransactionAuthorizationRequest;
import com.nuera.banking.card.model.dto.TransactionAuthorizationResponse;
import com.nuera.banking.card.model.dto.TransactionResponse;
import com.nuera.banking.card.service.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/transactions")
@Tag(name = "Transactions API", description = "Transaction authorization and transaction history ledger")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/authorize")
    @Operation(summary = "Authorise transactions with daily limit check and salted BCrypt PIN verification")
    public ResponseEntity<TransactionAuthorizationResponse> authorizeTransaction(
            @Valid @RequestBody TransactionAuthorizationRequest request) {
        TransactionAuthorizationResponse response = transactionService.authorizeTransaction(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{transactionId}")
    @Operation(summary = "Get transaction details by ID")
    public ResponseEntity<TransactionResponse> getTransactionById(@PathVariable String transactionId) {
        return ResponseEntity.ok(transactionService.getTransactionById(transactionId));
    }

    @GetMapping("/card/{cardId}")
    @Operation(summary = "Retrieve transaction history for a debit card")
    public ResponseEntity<List<TransactionResponse>> getTransactionsForCard(@PathVariable String cardId) {
        return ResponseEntity.ok(transactionService.getTransactionsForCard(cardId));
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "Retrieve transaction history for a bank account")
    public ResponseEntity<List<TransactionResponse>> getTransactionsForAccount(@PathVariable String accountId) {
        return ResponseEntity.ok(transactionService.getTransactionsForAccount(accountId));
    }
}
