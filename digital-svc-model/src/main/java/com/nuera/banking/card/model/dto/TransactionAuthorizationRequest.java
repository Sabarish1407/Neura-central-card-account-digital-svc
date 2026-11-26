package com.nuera.banking.card.model.dto;

import com.nuera.banking.card.model.enums.TransactionChannel;
import com.nuera.banking.card.model.enums.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class TransactionAuthorizationRequest {

    /**
     * Card number (16 digits) or Card Token
     */
    @NotBlank(message = "Card number or token is mandatory")
    private String cardNumber;

    @NotBlank(message = "PIN is mandatory for transaction authorization")
    private String pin;

    @NotNull(message = "Amount is mandatory")
    @DecimalMin(value = "0.01", message = "Transaction amount must be greater than zero")
    private BigDecimal amount;

    private String currency = "USD";

    private TransactionType transactionType = TransactionType.POS_PURCHASE;

    private TransactionChannel channel = TransactionChannel.POS_TERMINAL;

    private String merchantName;

    public TransactionAuthorizationRequest() {
    }

    public TransactionAuthorizationRequest(String cardNumber, String pin, BigDecimal amount) {
        this.cardNumber = cardNumber;
        this.pin = pin;
        this.amount = amount;
    }

    public TransactionAuthorizationRequest(String cardNumber, String pin, BigDecimal amount,
                                          TransactionType transactionType, TransactionChannel channel) {
        this.cardNumber = cardNumber;
        this.pin = pin;
        this.amount = amount;
        this.transactionType = transactionType;
        this.channel = channel;
    }

    public String getCardNumber() {
        return cardNumber;
    }

    public void setCardNumber(String cardNumber) {
        this.cardNumber = cardNumber;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public TransactionChannel getChannel() {
        return channel;
    }

    public void setChannel(TransactionChannel channel) {
        this.channel = channel;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }
}
