package com.nuera.banking.card.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class CardActivationRequest {

    private String cardId;

    @NotBlank(message = "CVV is mandatory")
    @Pattern(regexp = "^[0-9]{3}$", message = "CVV must be exactly 3 digits")
    private String cvv;

    @NotBlank(message = "4-digit PIN is mandatory")
    @Pattern(regexp = "^[0-9]{4}$", message = "PIN must be exactly 4 digits")
    private String pin;

    public CardActivationRequest() {
    }

    public CardActivationRequest(String cardId, String cvv, String pin) {
        this.cardId = cardId;
        this.cvv = cvv;
        this.pin = pin;
    }

    public String getCardId() {
        return cardId;
    }

    public void setCardId(String cardId) {
        this.cardId = cardId;
    }

    public String getCvv() {
        return cvv;
    }

    public void setCvv(String cvv) {
        this.cvv = cvv;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }
}
