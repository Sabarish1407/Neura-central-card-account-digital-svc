package com.nuera.banking.card.model.dto;

import com.nuera.banking.card.model.enums.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class AccountCreateRequest {

    @NotBlank(message = "Customer ID is mandatory")
    @Size(max = 64, message = "Customer ID must be at most 64 characters")
    private String customerId;

    @NotBlank(message = "Customer name is mandatory")
    @Size(max = 255, message = "Customer name must be at most 255 characters")
    private String customerName;

    private AccountType accountType = AccountType.CHECKING;

    @NotNull(message = "Initial deposit cannot be null")
    @DecimalMin(value = "0.00", message = "Initial deposit must be non-negative")
    private BigDecimal initialDeposit = BigDecimal.ZERO;

    @NotBlank(message = "Currency is mandatory")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter ISO code such as USD")
    private String currency = "USD";

    public AccountCreateRequest() {
    }

    public AccountCreateRequest(String customerId, String customerName, AccountType accountType,
                                BigDecimal initialDeposit, String currency) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.accountType = accountType;
        this.initialDeposit = initialDeposit;
        this.currency = currency;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public void setAccountType(AccountType accountType) {
        this.accountType = accountType;
    }

    public BigDecimal getInitialDeposit() {
        return initialDeposit;
    }

    public void setInitialDeposit(BigDecimal initialDeposit) {
        this.initialDeposit = initialDeposit;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
