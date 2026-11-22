package com.nuera.banking.card.service.entity;

import com.nuera.banking.card.model.enums.CardNetwork;
import com.nuera.banking.card.model.enums.CardStatus;
import com.nuera.banking.card.model.enums.CardType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "cards", indexes = {
    @Index(name = "idx_card_token", columnList = "card_token", unique = true),
    @Index(name = "idx_account_id", columnList = "account_id")
})
public class CardEntity {

    @Id
    @Column(name = "card_id", length = 64)
    private String cardId;

    @Column(name = "card_token", unique = true, nullable = false, length = 64)
    private String cardToken;

    /**
     * SHA-256 hash of card number for fast O(1) indexed lookup without exposing plain text.
     */
    @Column(name = "card_number_hash", unique = true, nullable = false, length = 64)
    private String cardNumberHash;

    /**
     * Stored AES-256-GCM encrypted card number (Base64). Never plain text!
     */
    @Column(name = "card_number_encrypted", nullable = false, length = 512)
    private String cardNumberEncrypted;

    @Column(name = "card_number_masked", nullable = false, length = 32)
    private String cardNumberMasked;

    @Column(name = "last_four_digits", nullable = false, length = 4)
    private String lastFourDigits;

    @Column(name = "card_holder_name", nullable = false)
    private String cardHolderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType = CardType.DEBIT;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_network", nullable = false)
    private CardNetwork cardNetwork = CardNetwork.VISA;

    @Column(name = "expiry_date", nullable = false, length = 8)
    private String expiryDate;

    /**
     * Stored CVV hash. Never plain text.
     */
    @Column(name = "cvv_hash", nullable = false)
    private String cvvHash;

    /**
     * Stored salted BCrypt PIN hash ($2a$10$...). Never plain text!
     */
    @Column(name = "pin_hash")
    private String pinHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_status", nullable = false)
    private CardStatus cardStatus = CardStatus.INACTIVE;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "USD";

    @Column(name = "daily_limit", nullable = false, precision = 19, scale = 2)
    private BigDecimal dailyLimit = new BigDecimal("1000.00");

    @Column(name = "failed_pin_attempts", nullable = false)
    private int failedPinAttempts = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    public CardEntity() {
    }

    public CardEntity(String cardId, String cardToken, String cardNumberEncrypted, String cardNumberHash,
                      String cardNumberMasked, String accountId, String cardHolderName, CardNetwork cardNetwork,
                      String cvvHash, String expiryDate, BigDecimal dailyLimit) {
        this.cardId = cardId;
        this.cardToken = cardToken;
        this.cardNumberEncrypted = cardNumberEncrypted;
        this.cardNumberHash = cardNumberHash;
        this.cardNumberMasked = cardNumberMasked;
        this.lastFourDigits = cardNumberMasked.length() >= 4 ? cardNumberMasked.substring(cardNumberMasked.length() - 4) : "0000";
        this.accountId = accountId;
        this.cardHolderName = cardHolderName;
        this.cardNetwork = cardNetwork;
        this.cardType = CardType.DEBIT;
        this.cvvHash = cvvHash;
        this.expiryDate = expiryDate;
        this.dailyLimit = dailyLimit;
        this.cardStatus = CardStatus.INACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getCardToken() {
        return cardToken;
    }

    public void setCardToken(String cardToken) {
        this.cardToken = cardToken;
    }

    public String getCardNumberHash() {
        return cardNumberHash;
    }

    public void setCardNumberHash(String cardNumberHash) {
        this.cardNumberHash = cardNumberHash;
    }

    public String getCardNumberEncrypted() {
        return cardNumberEncrypted;
    }

    public void setCardNumberEncrypted(String cardNumberEncrypted) {
        this.cardNumberEncrypted = cardNumberEncrypted;
    }

    public String getCardNumberMasked() {
        return cardNumberMasked;
    }

    public void setCardNumberMasked(String cardNumberMasked) {
        this.cardNumberMasked = cardNumberMasked;
    }

    public String getLastFourDigits() {
        return lastFourDigits;
    }

    public void setLastFourDigits(String lastFourDigits) {
        this.lastFourDigits = lastFourDigits;
    }

    public String getCardHolderName() {
        return cardHolderName;
    }

    public void setCardHolderName(String cardHolderName) {
        this.cardHolderName = cardHolderName;
    }

    public String getCardholderName() {
        return cardHolderName;
    }

    public void setCardholderName(String cardholderName) {
        this.cardHolderName = cardholderName;
    }

    public CardType getCardType() {
        return cardType;
    }

    public void setCardType(CardType cardType) {
        this.cardType = cardType;
    }

    public CardType getType() {
        return cardType;
    }

    public void setType(CardType type) {
        this.cardType = type;
    }

    public CardNetwork getCardNetwork() {
        return cardNetwork;
    }

    public void setCardNetwork(CardNetwork cardNetwork) {
        this.cardNetwork = cardNetwork;
    }

    public CardNetwork getNetwork() {
        return cardNetwork;
    }

    public void setNetwork(CardNetwork network) {
        this.cardNetwork = network;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    public String getCvvHash() {
        return cvvHash;
    }

    public void setCvvHash(String cvvHash) {
        this.cvvHash = cvvHash;
    }

    public String getPinHash() {
        return pinHash;
    }

    public void setPinHash(String pinHash) {
        this.pinHash = pinHash;
    }

    public CardStatus getCardStatus() {
        return cardStatus;
    }

    public void setCardStatus(CardStatus cardStatus) {
        this.cardStatus = cardStatus;
    }

    public CardStatus getStatus() {
        return cardStatus;
    }

    public void setStatus(CardStatus status) {
        this.cardStatus = status;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public int getFailedPinAttempts() {
        return failedPinAttempts;
    }

    public void setFailedPinAttempts(int failedPinAttempts) {
        this.failedPinAttempts = failedPinAttempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
