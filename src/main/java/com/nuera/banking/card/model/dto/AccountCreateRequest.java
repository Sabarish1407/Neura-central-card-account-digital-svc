package com.nuera.banking.card.model.dto;

import com.nuera.banking.card.model.enums.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class AccountCreateRequest {

    @NotBlank(message = "Customer ID is mandatory")
    private String customerId;

    @NotBlank(message = "Customer name is mandatory")
    private String customerName;

    private AccountType accountType = AccountType.CHECKING;

    @NotNull(message = "Initial deposit cannot be null")
    @DecimalMin(value = "0.00", message = "Initial deposit must be non-negative")
    private BigDecimal initialDeposit = BigDecimal.ZERO;

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
