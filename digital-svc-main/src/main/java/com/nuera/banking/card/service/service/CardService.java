package com.nuera.banking.card.service.service;

import com.nuera.banking.card.model.dto.*;
import com.nuera.banking.card.model.enums.AccountStatus;
import com.nuera.banking.card.model.enums.CardNetwork;
import com.nuera.banking.card.model.enums.CardStatus;
import com.nuera.banking.card.model.enums.CardType;
import com.nuera.banking.card.service.entity.AccountEntity;
import com.nuera.banking.card.service.entity.CardEntity;
import com.nuera.banking.card.service.exception.BankingException;
import com.nuera.banking.card.service.exception.ResourceNotFoundException;
import com.nuera.banking.card.service.repository.AccountRepository;
import com.nuera.banking.card.service.repository.CardRepository;
import com.nuera.banking.card.service.util.CardSecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CardService {

    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;
    private final EncryptionService encryptionService;

    public CardService(CardRepository cardRepository,
                       AccountRepository accountRepository,
                       EncryptionService encryptionService) {
        this.cardRepository = cardRepository;
        this.accountRepository = accountRepository;
        this.encryptionService = encryptionService;
    }

    /**
     * Issues a new VISA debit card on an active account.
     * Card number is encrypted with AES-256 and tokenized; never stored in plain text.
     */
    @Transactional
    public CardResponse issueDebitCard(CardIssuanceRequest request) {
        AccountEntity account = accountRepository.findById(request.getAccountId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found with ID: " + request.getAccountId()));

        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new BankingException("Cannot issue debit card for inactive account", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        String rawCardNumber = CardSecurityUtils.generateVisaCardNumber();
        String cvv = CardSecurityUtils.generateCvv();
        String expiry = CardSecurityUtils.generateExpiryDate(3);
        String cardId = "crd-" + UUID.randomUUID().toString().substring(0, 8);
        String cardToken = CardSecurityUtils.generateCardToken();
        String masked = CardSecurityUtils.maskCardNumber(rawCardNumber);
        String last4 = rawCardNumber.substring(rawCardNumber.length() - 4);

        CardEntity card = new CardEntity();
        card.setCardId(cardId);
        card.setCardToken(cardToken);
        card.setCardNumberEncrypted(encryptionService.encrypt(rawCardNumber));
        card.setCardNumberHash(CardSecurityUtils.hashCardNumber(rawCardNumber));
        card.setCardNumberMasked(masked);
        card.setLastFourDigits(last4);
        card.setCardHolderName(request.getCardHolderName() != null ? request.getCardHolderName().toUpperCase() : "ACCOUNT HOLDER");
        card.setCardType(CardType.DEBIT);
        card.setCardNetwork(CardNetwork.VISA);
        card.setExpiryDate(expiry);
        card.setCvvHash(CardSecurityUtils.hashCvv(cvv));
        card.setPinHash(null); // Set upon activation
        card.setCardStatus(CardStatus.INACTIVE);
        card.setAccountId(account.getAccountId());
        card.setCurrency(request.getCurrency() != null ? request.getCurrency() : account.getCurrency());
        card.setDailyLimit(request.getDailyLimit() != null ? request.getDailyLimit() : new BigDecimal("1000.00"));
        card.setFailedPinAttempts(0);
        card.setCreatedAt(Instant.now());
        card.setUpdatedAt(Instant.now());

        CardEntity saved = cardRepository.save(card);
        CardResponse response = mapToResponse(saved);
        response.setCvv(cvv); // Returned only upon initial issuance for activation
        return response;
    }

    /**
     * Activates debit card with CVV and establishes a 4-digit PIN.
     * PIN is stored as a salted BCrypt hash, never plain text.
     */
    @Transactional
    public CardResponse activateDebitCard(CardActivationRequest request) {
        CardEntity card = cardRepository.findById(request.getCardId())
                .orElseThrow(() -> new ResourceNotFoundException("Card not found with ID: " + request.getCardId()));

        if (card.getCardStatus() == CardStatus.ACTIVE) {
            throw new BankingException("Card is already active", HttpStatus.CONFLICT);
        }
        if (card.getCardStatus() == CardStatus.BLOCKED || card.getCardStatus() == CardStatus.LOST_OR_STOLEN) {
            throw new BankingException("Cannot activate a blocked or lost/stolen card", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // Verify CVV
        String providedCvvHash = CardSecurityUtils.hashCvv(request.getCvv());
        if (!providedCvvHash.equalsIgnoreCase(card.getCvvHash())) {
            throw new BankingException("Invalid CVV provided for activation", HttpStatus.BAD_REQUEST);
        }

        // Store PIN as salted BCrypt hash
        card.setPinHash(CardSecurityUtils.hashPin(request.getPin()));
        card.setCardStatus(CardStatus.ACTIVE);
        card.setFailedPinAttempts(0);
        card.setUpdatedAt(Instant.now());

        CardEntity saved = cardRepository.save(card);
        return mapToResponse(saved);
    }

    /**
     * Freezes an active debit card.
     */
    @Transactional
    public CardResponse freezeCard(String cardId) {
        CardStatusUpdateRequest req = new CardStatusUpdateRequest();
        req.setStatus(CardStatus.FROZEN);
        return updateCardStatus(cardId, req);
    }

    /**
     * Unfreezes a frozen debit card.
     */
    @Transactional
    public CardResponse unfreezeCard(String cardId) {
        CardStatusUpdateRequest req = new CardStatusUpdateRequest();
        req.setStatus(CardStatus.ACTIVE);
        return updateCardStatus(cardId, req);
    }

    /**
     * Updates card status (e.g. freeze, unfreeze, block).
     */
    @Transactional
    public CardResponse updateCardStatus(String cardId, CardStatusUpdateRequest request) {
        CardEntity card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found with ID: " + cardId));

        if (card.getCardStatus() == CardStatus.LOST_OR_STOLEN) {
            throw new BankingException("Card is reported lost or stolen and cannot be updated", HttpStatus.UNPROCESSABLE_ENTITY);
        }

        card.setCardStatus(request.getStatus());
        card.setUpdatedAt(Instant.now());
        CardEntity saved = cardRepository.save(card);
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public CardResponse getCardById(String cardId) {
        CardEntity card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found with ID: " + cardId));
        return mapToResponse(card);
    }

    @Transactional(readOnly = true)
    public List<CardResponse> getCardsByAccountId(String accountId) {
        return cardRepository.findByAccountId(accountId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CardResponse updateControls(String cardId, CardControlsDto controls) {
        CardEntity card = cardRepository.findById(cardId)
                .orElseThrow(() -> new ResourceNotFoundException("Card not found with ID: " + cardId));

        card.setDailyLimit(controls.getDailyLimit());
        card.setUpdatedAt(Instant.now());
        CardEntity saved = cardRepository.save(card);
        return mapToResponse(saved);
    }

    public CardResponse mapToResponse(CardEntity entity) {
        CardResponse res = new CardResponse();
        res.setCardId(entity.getCardId());
        res.setMaskedCardNumber(entity.getCardNumberMasked());
        res.setCardToken(entity.getCardToken());
        res.setCardHolderName(entity.getCardHolderName());
        res.setCardType(entity.getCardType());
        res.setCardNetwork(entity.getCardNetwork());
        res.setExpiryDate(entity.getExpiryDate());
        res.setCardStatus(entity.getCardStatus());
        res.setAccountId(entity.getAccountId());
        res.setCurrency(entity.getCurrency());
        res.setDailyLimit(entity.getDailyLimit());
        res.setFailedPinAttempts(entity.getFailedPinAttempts());
        res.setCreatedAt(entity.getCreatedAt());
        res.setUpdatedAt(entity.getUpdatedAt());
        return res;
    }
}
