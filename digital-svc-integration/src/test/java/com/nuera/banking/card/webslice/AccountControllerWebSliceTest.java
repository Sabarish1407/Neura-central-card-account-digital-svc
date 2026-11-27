package com.nuera.banking.card.webslice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuera.banking.card.model.dto.AccountCreateRequest;
import com.nuera.banking.card.model.dto.AccountDepositRequest;
import com.nuera.banking.card.model.dto.AccountResponse;
import com.nuera.banking.card.model.enums.AccountStatus;
import com.nuera.banking.card.model.enums.AccountType;
import com.nuera.banking.card.service.controller.AccountController;
import com.nuera.banking.card.service.service.AccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;

@Tag("neuro-component")
@WebMvcTest(AccountController.class)
@DisplayName("Web Slice Tests: AccountController")
class AccountControllerWebSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AccountService accountService;

    @Test
    @DisplayName("POST /api/v1/accounts creates account and returns 201 Created")
    void testCreateAccountEndpoint() throws Exception {
        AccountResponse response = new AccountResponse();
        response.setAccountId("acc-001");
        response.setAccountNumber("1012345678");
        response.setCustomerId("cust-01");
        response.setCustomerName("Alice");
        response.setAccountType(AccountType.CHECKING);
        response.setBalance(BigDecimal.valueOf(100.00));
        response.setStatus(AccountStatus.ACTIVE);

        when(accountService.createAccount(any(AccountCreateRequest.class))).thenReturn(response);

        AccountCreateRequest request = new AccountCreateRequest();
        request.setCustomerId("cust-01");
        request.setCustomerName("Alice");
        request.setAccountType(AccountType.CHECKING);
        request.setInitialDeposit(BigDecimal.valueOf(100.00));

        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value("acc-001"))
                .andExpect(jsonPath("$.customerName").value("Alice"))
                .andExpect(jsonPath("$.balance").value(100.00));
    }

    @Test
    @DisplayName("POST /api/v1/accounts/{accountId}/deposit deposits money and returns 200 OK")
    void testDepositEndpoint() throws Exception {
        AccountResponse response = new AccountResponse();
        response.setAccountId("acc-001");
        response.setBalance(BigDecimal.valueOf(250.00));

        when(accountService.depositFunds(eq("acc-001"), any(AccountDepositRequest.class))).thenReturn(response);

        AccountDepositRequest request = new AccountDepositRequest();
        request.setAmount(BigDecimal.valueOf(150.00));

        mockMvc.perform(post("/api/v1/accounts/acc-001/deposit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-001"))
                .andExpect(jsonPath("$.balance").value(250.00));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{accountId} returns account details")
    void testGetAccountEndpoint() throws Exception {
        AccountResponse response = new AccountResponse();
        response.setAccountId("acc-001");
        response.setCustomerName("Alice");

        when(accountService.getAccountById("acc-001")).thenReturn(response);

        mockMvc.perform(get("/api/v1/accounts/acc-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("acc-001"))
                .andExpect(jsonPath("$.customerName").value("Alice"));
    }
}
