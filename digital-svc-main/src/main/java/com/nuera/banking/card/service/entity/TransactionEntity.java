package com.nuera.banking.card.service.entity;

import com.nuera.banking.card.model.enums.DeclineReason;
import com.nuera.banking.card.model.enums.TransactionChannel;
import com.nuera.banking.card.model.enums.TransactionStatus;
import com.nuera.banking.card.model.enums.TransactionType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "card_transactions", indexes = {
    @Index(name = "idx_txn_card_id", columnList = "card_id"),
    @Index(name = "idx_txn_ref_num", columnList = "reference_number", unique = true)
})
public class TransactionEntity {

    @Id
    @Column(name = "transaction_id", length = 64)
    private String transactionId;

    @Column(name = "card_id", nullable = false, length = 64)
    private String cardId;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Column(name = "reference_number", unique = true, nullable = false, length = 64)
    private String referenceNumber;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 8)
    private String currency = "USD";

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private TransactionType transactionType = TransactionType.POS_PURCHASE;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false)
    private TransactionChannel channel = TransactionChannel.POS_TERMINAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TransactionStatus status;

    @Column(name = "merchant_name")
    private String merchantName;

    @Column(name = "authorization_code", length = 32)
    private String authorizationCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "decline_reason", nullable = false)
    private DeclineReason declineReason = DeclineReason.NONE;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp = Instant.now();

    public TransactionEntity() {
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getReferenceNumber() {
        return referenceNumber;
    }

    public void setReferenceNumber(String referenceNumber) {
        this.referenceNumber = referenceNumber;
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

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public String getMerchantName() {
        return merchantName;
    }

    public void setMerchantName(String merchantName) {
        this.merchantName = merchantName;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public void setAuthorizationCode(String authorizationCode) {
        this.authorizationCode = authorizationCode;
    }

    public DeclineReason getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(DeclineReason declineReason) {
        this.declineReason = declineReason;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
