package com.nuera.banking.card.integration;

import com.nuera.banking.card.model.dto.AccountCreateRequest;
import com.nuera.banking.card.model.dto.AccountResponse;
import com.nuera.banking.card.model.dto.CardIssuanceRequest;
import com.nuera.banking.card.model.dto.ErrorResponse;
import com.nuera.banking.card.model.enums.AccountType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@DisplayName("HTTP Integration Tests: Input Validation and Error Sanitization")
class ValidationAndSecurityHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    @DisplayName("Account creation with valid 3-letter currency USD answers 201 Created")
    void testValidCurrencyAnswers201() {
        AccountCreateRequest request = new AccountCreateRequest();
        request.setCustomerId("cust-val-01");
        request.setCustomerName("Valid Currency User");
        request.setAccountType(AccountType.CHECKING);
        request.setInitialDeposit(new BigDecimal("500.00"));
        request.setCurrency("USD");

        ResponseEntity<AccountResponse> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/accounts",
                request,
                AccountResponse.class
        );

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("USD", response.getBody().getCurrency());
    }

    @Test
    @DisplayName("Account creation with 10-character currency answers 400 Bad Request")
    void test10CharCurrencyAnswers400() {
        AccountCreateRequest request = new AccountCreateRequest();
        request.setCustomerId("cust-val-02");
        request.setCustomerName("Invalid Currency User");
        request.setAccountType(AccountType.CHECKING);
        request.setInitialDeposit(new BigDecimal("500.00"));
        request.setCurrency("TOOLONGCUR"); // 10 characters

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/accounts",
                request,
                ErrorResponse.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
    }

    @Test
    @DisplayName("Failure responses contain no internal SQL or database error text")
    void testFailureResponseContainsNoSqlText() {
        // Trigger validation failure with invalid customerId and currency
        AccountCreateRequest request = new AccountCreateRequest();
        request.setCustomerId("");
        request.setCustomerName("");
        request.setCurrency("TOOLONGCUR");

        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/accounts",
                request,
                String.class
        );

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        String body = response.getBody();
        assertNotNull(body);

        String lowerBody = body.toLowerCase();
        assertFalse(lowerBody.contains("sql"), "Response must not contain 'sql' keyword");
        assertFalse(lowerBody.contains("syntax error"), "Response must not contain SQL syntax errors");
        assertFalse(lowerBody.contains("hibernate"), "Response must not expose Hibernate internals");
        assertFalse(lowerBody.contains("psqlexception"), "Response must not expose PostgreSQL driver exceptions");
        assertFalse(lowerBody.contains("h2exception"), "Response must not expose H2 database exceptions");
    }

    @Test
    @DisplayName("Card issuance for an unknown account answers 404 Not Found")
    void testCardIssuanceForUnknownAccountAnswers404() {
        CardIssuanceRequest request = new CardIssuanceRequest();
        request.setAccountId("non-existent-account-id-999");
        request.setCardHolderName("Ghost User");
        request.setDailyLimit(new BigDecimal("1000.00"));

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/cards",
                request,
                ErrorResponse.class
        );

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().getStatus());
    }
}
