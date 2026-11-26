package com.nuera.banking.card.model.dto;

import com.nuera.banking.card.model.enums.CardNetwork;
import com.nuera.banking.card.model.enums.CardType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class CardIssuanceRequest {

    @NotBlank(message = "Account ID is mandatory")
    @Size(max = 64, message = "Account ID must be at most 64 characters")
    private String accountId;

    @NotBlank(message = "Cardholder name is mandatory")
    @Size(max = 255, message = "Cardholder name must be at most 255 characters")
    private String cardHolderName;

    private CardType cardType = CardType.DEBIT;

    private CardNetwork cardNetwork = CardNetwork.VISA;

    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code such as USD")
    private String currency = "USD";

    @DecimalMin(value = "0.00", message = "Daily spending limit must be non-negative")
    private BigDecimal dailyLimit = new BigDecimal("1000.00");

    public CardIssuanceRequest() {
    }

    public CardIssuanceRequest(String accountId, String cardHolderName) {
        this.accountId = accountId;
        this.cardHolderName = cardHolderName;
    }

    public CardIssuanceRequest(String accountId, String cardHolderName, BigDecimal dailyLimit) {
        this.accountId = accountId;
        this.cardHolderName = cardHolderName;
        this.dailyLimit = dailyLimit;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
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
}
