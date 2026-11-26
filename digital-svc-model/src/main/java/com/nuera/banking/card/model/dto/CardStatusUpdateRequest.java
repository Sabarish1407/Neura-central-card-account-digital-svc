package com.nuera.banking.card.model.dto;

import com.nuera.banking.card.model.enums.CardStatus;
import jakarta.validation.constraints.NotNull;

public class CardStatusUpdateRequest {

    @NotNull(message = "Card status cannot be null")
    private CardStatus status;

    private String reason;

    public CardStatusUpdateRequest() {
    }

    public CardStatusUpdateRequest(CardStatus status) {
        this.status = status;
    }

    public CardStatusUpdateRequest(CardStatus status, String reason) {
        this.status = status;
        this.reason = reason;
    }

    public CardStatus getStatus() {
        return status;
    }

    public void setStatus(CardStatus status) {
        this.status = status;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
