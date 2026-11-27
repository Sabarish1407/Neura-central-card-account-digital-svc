package com.nuera.banking.card.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.nuera.banking.card.model.enums.DeclineReason;
import com.nuera.banking.card.model.enums.TransactionStatus;
import java.math.BigDecimal;
import java.time.Instant;

public class TransactionAuthorizationResponse {

    private boolean authorized;
    private String authorizationCode;
    @JsonProperty("approvalCode")
    private String approvalCode;
    private String transactionId;
    private TransactionStatus status;
    private DeclineReason declineReason;
    private String message;
    private BigDecimal amount;
    private String currency;
    private BigDecimal remainingDailyLimit;
    private Instant timestamp;

    public TransactionAuthorizationResponse() {
    }

    public static TransactionAuthorizationResponse approved(String authorizationCode, String transactionId,
                                                             BigDecimal remainingDailyLimit) {
        TransactionAuthorizationResponse res = new TransactionAuthorizationResponse();
        res.setAuthorized(true);
        res.setAuthorizationCode(authorizationCode);
        res.setApprovalCode(authorizationCode);
        res.setTransactionId(transactionId);
        res.setStatus(TransactionStatus.APPROVED);
        res.setDeclineReason(DeclineReason.NONE);
        res.setMessage("Transaction authorized successfully");
        res.setRemainingDailyLimit(remainingDailyLimit);
        res.setTimestamp(Instant.now());
        return res;
    }

    public static TransactionAuthorizationResponse declined(DeclineReason reason, String message, String transactionId) {
        TransactionAuthorizationResponse res = new TransactionAuthorizationResponse();
        res.setAuthorized(false);
        res.setAuthorizationCode(null);
        res.setApprovalCode(null);
        res.setTransactionId(transactionId);
        res.setStatus(TransactionStatus.DECLINED);
        res.setDeclineReason(reason);
        res.setMessage(message);
        res.setRemainingDailyLimit(null);
        res.setTimestamp(Instant.now());
        return res;
    }

    public boolean isAuthorized() {
        return authorized;
    }

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
    }

    public String getAuthorizationCode() {
        return authorizationCode;
    }

    public void setAuthorizationCode(String authorizationCode) {
        this.authorizationCode = authorizationCode;
    }

    public String getApprovalCode() {
        return approvalCode;
    }

    public void setApprovalCode(String approvalCode) {
        this.approvalCode = approvalCode;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public DeclineReason getDeclineReason() {
        return declineReason;
    }

    public void setDeclineReason(DeclineReason declineReason) {
        this.declineReason = declineReason;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
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

    public BigDecimal getRemainingDailyLimit() {
        return remainingDailyLimit;
    }

    public void setRemainingDailyLimit(BigDecimal remainingDailyLimit) {
        this.remainingDailyLimit = remainingDailyLimit;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
