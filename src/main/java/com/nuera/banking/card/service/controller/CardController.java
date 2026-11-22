package com.nuera.banking.card.service.controller;

import com.nuera.banking.card.model.dto.*;
import com.nuera.banking.card.service.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/cards")
@Tag(name = "Cards API", description = "Debit card issuance, activation, status transitions, and spend controls")
public class CardController {

    private final CardService cardService;

    public CardController(CardService cardService) {
        this.cardService = cardService;
    }

    @PostMapping("/issue")
    @Operation(summary = "Issue a VISA debit card on an active account")
    public ResponseEntity<CardResponse> issueDebitCard(@Valid @RequestBody CardIssuanceRequest request) {
        CardResponse response = cardService.issueDebitCard(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{cardId}/activate")
    @Operation(summary = "Activate debit card with CVV and set 4-digit PIN (salted BCrypt hash)")
    public ResponseEntity<CardResponse> activateDebitCard(@PathVariable String cardId,
                                                          @Valid @RequestBody CardActivationRequest request) {
        request.setCardId(cardId);
        CardResponse response = cardService.activateDebitCard(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{cardId}")
    @Operation(summary = "Get debit card details with masked card number")
    public ResponseEntity<CardResponse> getCardById(@PathVariable String cardId) {
        return ResponseEntity.ok(cardService.getCardById(cardId));
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "List all debit cards linked to an account")
    public ResponseEntity<List<CardResponse>> getCardsByAccountId(@PathVariable String accountId) {
        return ResponseEntity.ok(cardService.getCardsByAccountId(accountId));
    }

    @PostMapping("/{cardId}/freeze")
    @Operation(summary = "Freeze a debit card")
    public ResponseEntity<CardResponse> freezeCard(@PathVariable String cardId) {
        return ResponseEntity.ok(cardService.freezeCard(cardId));
    }

    @PostMapping("/{cardId}/unfreeze")
    @Operation(summary = "Unfreeze a debit card")
    public ResponseEntity<CardResponse> unfreezeCard(@PathVariable String cardId) {
        return ResponseEntity.ok(cardService.unfreezeCard(cardId));
    }

    @PutMapping("/{cardId}/status")
    @Operation(summary = "Update debit card status")
    public ResponseEntity<CardResponse> updateCardStatus(@PathVariable String cardId,
                                                          @Valid @RequestBody CardStatusUpdateRequest request) {
        return ResponseEntity.ok(cardService.updateCardStatus(cardId, request));
    }

    @PutMapping("/{cardId}/controls")
    @Operation(summary = "Update card daily spending limit")
    public ResponseEntity<CardResponse> updateControls(@PathVariable String cardId,
                                                        @Valid @RequestBody CardControlsDto controls) {
        return ResponseEntity.ok(cardService.updateControls(cardId, controls));
    }
}
