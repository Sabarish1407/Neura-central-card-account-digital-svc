package com.nuera.banking.card.integration;

import com.nuera.banking.card.model.dto.*;
import com.nuera.banking.card.model.enums.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end post-deploy verification test.
 * Tagged with "neuro-post-deploy" for Central Neuro execution against deployed environments.
 * Reads target host dynamically via system property 'app.base-url' or environment variable 'APP_BASE_URL'.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
@Tag("neuro-post-deploy")
@DisplayName("Post-Deploy Integration Tests: Full Debit Card Flow")
class NeuroPostDeployFullFlowIntegrationTest {

    @LocalServerPort
    private int port;

    private final RestTemplate restTemplate = new RestTemplate();

    private String getBaseUrl() {
        String url = System.getProperty("app.base-url");
        if (url == null || url.trim().isEmpty()) {
            url = System.getenv("APP_BASE_URL");
        }
        if (url == null || url.trim().isEmpty()) {
            url = "http://localhost:" + port;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    @Test
    @DisplayName("Execute full banking flow: create account, verify deposit, issue card, activate, authorize")
    void testFullBankingFlowPostDeploy() {
        String baseUrl = getBaseUrl();

        // Step 1: Create Account with initial deposit
        AccountCreateRequest accountReq = new AccountCreateRequest();
        accountReq.setCustomerId("cust-postdeploy-01");
        accountReq.setCustomerName("PostDeploy Verified Customer");
        accountReq.setAccountType(AccountType.CHECKING);
        accountReq.setInitialDeposit(new BigDecimal("1000.00"));
        accountReq.setCurrency("USD");

        ResponseEntity<AccountResponse> accountRes = restTemplate.postForEntity(
                baseUrl + "/api/v1/accounts",
                accountReq,
                AccountResponse.class
        );

        assertEquals(HttpStatus.CREATED, accountRes.getStatusCode());
        assertNotNull(accountRes.getBody());
        String accountId = accountRes.getBody().getAccountId();
        assertNotNull(accountId);
        assertEquals(new BigDecimal("1000.00"), accountRes.getBody().getBalance());

        // Step 2: Issue Debit Card
        CardIssuanceRequest cardReq = new CardIssuanceRequest();
        cardReq.setAccountId(accountId);
        cardReq.setCardHolderName("PostDeploy Customer");
        cardReq.setDailyLimit(new BigDecimal("500.00"));
        cardReq.setCurrency("USD");

        ResponseEntity<CardResponse> cardRes = restTemplate.postForEntity(
                baseUrl + "/api/v1/cards",
                cardReq,
                CardResponse.class
        );

        assertEquals(HttpStatus.CREATED, cardRes.getStatusCode());
        assertNotNull(cardRes.getBody());
        String cardId = cardRes.getBody().getCardId();
        String cardToken = cardRes.getBody().getCardToken();
        String cvv = cardRes.getBody().getCvv();
        assertNotNull(cardId);
        assertNotNull(cardToken);
        assertEquals(CardStatus.INACTIVE, cardRes.getBody().getCardStatus());

        // Step 3: Activate Card with PIN and CVV
        CardActivationRequest actReq = new CardActivationRequest();
        actReq.setCardId(cardId);
        actReq.setCvv(cvv);
        actReq.setPin("4321");

        ResponseEntity<CardResponse> actRes = restTemplate.postForEntity(
                baseUrl + "/api/v1/cards/activate",
                actReq,
                CardResponse.class
        );

        assertEquals(HttpStatus.OK, actRes.getStatusCode());
        assertNotNull(actRes.getBody());
        assertEquals(CardStatus.ACTIVE, actRes.getBody().getCardStatus());

        // Step 4: Authorize Transaction
        TransactionAuthorizationRequest authReq = new TransactionAuthorizationRequest();
        authReq.setCardNumber(cardToken);
        authReq.setPin("4321");
        authReq.setAmount(new BigDecimal("75.00"));
        authReq.setCurrency("USD");
        authReq.setMerchantName("PostDeploy Coffee Store");

        ResponseEntity<TransactionAuthorizationResponse> authRes = restTemplate.postForEntity(
                baseUrl + "/api/v1/transactions/authorize",
                authReq,
                TransactionAuthorizationResponse.class
        );

        assertEquals(HttpStatus.OK, authRes.getStatusCode());
        assertNotNull(authRes.getBody());
        assertEquals(TransactionStatus.APPROVED, authRes.getBody().getStatus());

        // Step 5: Verify Account Balance deducted
        ResponseEntity<AccountResponse> updatedAccountRes = restTemplate.getForEntity(
                baseUrl + "/api/v1/accounts/" + accountId,
                AccountResponse.class
        );

        assertEquals(HttpStatus.OK, updatedAccountRes.getStatusCode());
        assertNotNull(updatedAccountRes.getBody());
        assertEquals(new BigDecimal("925.00"), updatedAccountRes.getBody().getBalance());
    }
}
