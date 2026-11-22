package com.nuera.banking.card.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class CardControlsDto {

    @NotNull(message = "Daily limit cannot be null")
    @DecimalMin(value = "0.00", message = "Daily limit must be non-negative")
    private BigDecimal dailyLimit;

    public CardControlsDto() {
    }

    public CardControlsDto(BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
    }

    public BigDecimal getDailyLimit() {
        return dailyLimit;
    }

    public void setDailyLimit(BigDecimal dailyLimit) {
        this.dailyLimit = dailyLimit;
    }
}
