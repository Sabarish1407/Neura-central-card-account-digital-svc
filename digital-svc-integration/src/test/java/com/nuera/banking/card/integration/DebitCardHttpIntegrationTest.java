package com.nuera.banking.card.integration;

import com.nuera.banking.card.model.dto.*;
import com.nuera.banking.card.model.enums.*;
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
@DisplayName("HTTP Integration Tests: Debit Card Full Lifecycle")
class DebitCardHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    @DisplayName("Complete E2E: Account -> Card Issuance -> Activation -> Purchase -> Freeze/Unfreeze -> History")
    void testCompleteDebitCardLifecycle() {
        // Step 1: Open Account
        AccountCreateRequest accountReq = new AccountCreateRequest();
        accountReq.setCustomerId("cust-e2e-1");
        accountReq.setCustomerName("David Miller");
        accountReq.setAccountType(AccountType.CHECKING);
        accountReq.setInitialDeposit(BigDecimal.valueOf(1000.00));
        accountReq.setCurrency("USD");

        ResponseEntity<AccountResponse> accountRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/accounts",
                accountReq,
                AccountResponse.class
        );

        assertEquals(HttpStatus.CREATED, accountRes.getStatusCode());
        assertNotNull(accountRes.getBody());
        String accountId = accountRes.getBody().getAccountId();

        // Step 2: Issue VISA Debit Card
        CardIssuanceRequest cardReq = new CardIssuanceRequest();
        cardReq.setAccountId(accountId);
        cardReq.setCardHolderName("David Miller");
        cardReq.setDailyLimit(BigDecimal.valueOf(500.00));

        ResponseEntity<CardResponse> cardRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/cards/issue",
                cardReq,
                CardResponse.class
        );

        assertEquals(HttpStatus.CREATED, cardRes.getStatusCode());
        assertNotNull(cardRes.getBody());
        CardResponse card = cardRes.getBody();
        String cardId = card.getCardId();
        String cardToken = card.getCardToken();
        String cvv = card.getCvv(); // Returned once upon issuance

        assertEquals(CardStatus.INACTIVE, card.getCardStatus());
        assertEquals(CardNetwork.VISA, card.getCardNetwork());
        assertTrue(card.getMaskedCardNumber().startsWith("4"));

        // Step 3: Activate Card with CVV and set 4-digit PIN
        CardActivationRequest activateReq = new CardActivationRequest();
        activateReq.setCardId(cardId);
        activateReq.setCvv(cvv);
        activateReq.setPin("7890");

        ResponseEntity<CardResponse> activateRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/cards/" + cardId + "/activate",
                activateReq,
                CardResponse.class
        );

        assertEquals(HttpStatus.OK, activateRes.getStatusCode());
        assertNotNull(activateRes.getBody());
        assertEquals(CardStatus.ACTIVE, activateRes.getBody().getCardStatus());

        // Step 4: Authorize Purchase Transaction with PIN
        TransactionAuthorizationRequest authReq = new TransactionAuthorizationRequest();
        authReq.setCardNumber(cardToken);
        authReq.setPin("7890");
        authReq.setAmount(BigDecimal.valueOf(120.00));
        authReq.setCurrency("USD");
        authReq.setMerchantName("Apple Store");
        authReq.setChannel(TransactionChannel.POS);

        ResponseEntity<TransactionAuthorizationResponse> authRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize",
                authReq,
                TransactionAuthorizationResponse.class
        );

        assertEquals(HttpStatus.OK, authRes.getStatusCode());
        assertNotNull(authRes.getBody());
        assertEquals(TransactionStatus.APPROVED, authRes.getBody().getStatus());
        assertNotNull(authRes.getBody().getApprovalCode());

        // Step 5: Freeze Card
        ResponseEntity<CardResponse> freezeRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/cards/" + cardId + "/freeze",
                null,
                CardResponse.class
        );
        assertEquals(HttpStatus.OK, freezeRes.getStatusCode());
        assertEquals(CardStatus.FROZEN, freezeRes.getBody().getCardStatus());

        // Step 6: Authorization while frozen should be DECLINED
        TransactionAuthorizationRequest frozenAuthReq = new TransactionAuthorizationRequest();
        frozenAuthReq.setCardNumber(cardToken);
        frozenAuthReq.setPin("7890");
        frozenAuthReq.setAmount(BigDecimal.valueOf(50.00));
        frozenAuthReq.setCurrency("USD");

        ResponseEntity<TransactionAuthorizationResponse> frozenAuthRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize",
                frozenAuthReq,
                TransactionAuthorizationResponse.class
        );
        assertEquals(HttpStatus.OK, frozenAuthRes.getStatusCode());
        assertEquals(TransactionStatus.DECLINED, frozenAuthRes.getBody().getStatus());
        assertEquals(DeclineReason.CARD_FROZEN, frozenAuthRes.getBody().getDeclineReason());

        // Step 7: Unfreeze Card
        ResponseEntity<CardResponse> unfreezeRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/cards/" + cardId + "/unfreeze",
                null,
                CardResponse.class
        );
        assertEquals(HttpStatus.OK, unfreezeRes.getStatusCode());
        assertEquals(CardStatus.ACTIVE, unfreezeRes.getBody().getCardStatus());

        // Step 8: Transaction after unfreeze succeeds
        ResponseEntity<TransactionAuthorizationResponse> afterUnfreezeRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize",
                frozenAuthReq,
                TransactionAuthorizationResponse.class
        );
        assertEquals(HttpStatus.OK, afterUnfreezeRes.getStatusCode());
        assertEquals(TransactionStatus.APPROVED, afterUnfreezeRes.getBody().getStatus());

        // Step 9: Transaction History
        ResponseEntity<TransactionResponse[]> historyRes = restTemplate.getForEntity(
                getBaseUrl() + "/api/v1/transactions/card/" + cardId,
                TransactionResponse[].class
        );
        assertEquals(HttpStatus.OK, historyRes.getStatusCode());
        assertNotNull(historyRes.getBody());
        assertTrue(historyRes.getBody().length >= 3);
    }
}
