package com.nuera.banking.card.model.dto;

import com.nuera.banking.card.model.enums.CardNetwork;
import com.nuera.banking.card.model.enums.CardStatus;
import com.nuera.banking.card.model.enums.CardType;
import java.math.BigDecimal;
import java.time.Instant;

public class CardResponse {

    private String cardId;
    private String maskedCardNumber;
    private String cardToken;
    private String cardHolderName;
    private CardType cardType;
    private CardNetwork cardNetwork;
    private String expiryDate;
    private CardStatus cardStatus;
    private String accountId;
    private String currency;
    private BigDecimal dailyLimit;
    private int failedPinAttempts;
    private String cvv;
    private Instant createdAt;
    private Instant updatedAt;

    public CardResponse() {
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getMaskedCardNumber() {
        return maskedCardNumber;
    }

    public void setMaskedCardNumber(String maskedCardNumber) {
        this.maskedCardNumber = maskedCardNumber;
    }

    public String getCardToken() {
        return cardToken;
    }

    public void setCardToken(String cardToken) {
        this.cardToken = cardToken;
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

    public String getCvv() {
        return cvv;
    }

    public void setCvv(String cvv) {
        this.cvv = cvv;
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
