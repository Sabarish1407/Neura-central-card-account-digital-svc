package com.nuera.banking.card.e2e;

import com.nuera.banking.card.model.dto.*;
import com.nuera.banking.card.model.enums.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-End (E2E) Journey Tests for the Debit Card Digital Banking Service.
 *
 * Simulates complete end-to-end customer and operational journeys:
 * 1. Full Multi-Channel Retail Banking Journey (Onboarding -> Card Issuance -> Activation -> POS -> E-Commerce -> Daily Limit Exhaustion -> Control Limit Update -> Retry -> Freeze/Unfreeze -> Ledger Audit)
 * 2. Security Brute-Force Lockout Defense (3 failed PIN attempts -> auto-transition to BLOCKED -> lockout enforcement)
 * 3. Multi-Account / Multi-Card Portfolio Isolation
 * 4. Zero-Balance Exhaustion and Account Top-Up Recovery
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@Tag("e2e")
@DisplayName("End-to-End (E2E) Lifecycle Customer Journeys")
class DebitCardEndToEndLifecycleE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String getBaseUrl() {
        String override = System.getProperty("app.base-url", System.getenv("APP_BASE_URL"));
        if (override != null && !override.isBlank()) {
            return override.replaceAll("/+$", "");
        }
        return "http://localhost:" + port;
    }

    @Test
    @DisplayName("E2E Journey 1: Complete Retail Customer Journey (Sophia Chen)")
    void testCompleteRetailCustomerJourney() {
        // Step 1: Customer Onboarding - Open Checking Account with $3,500.00
        AccountCreateRequest accountReq = new AccountCreateRequest();
        accountReq.setCustomerId("cust-sophia-" + System.nanoTime());
        accountReq.setCustomerName("Sophia Chen");
        accountReq.setAccountType(AccountType.CHECKING);
        accountReq.setInitialDeposit(BigDecimal.valueOf(3500.00));
        accountReq.setCurrency("USD");

        ResponseEntity<AccountResponse> accountRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/accounts", accountReq, AccountResponse.class);
        assertEquals(HttpStatus.CREATED, accountRes.getStatusCode());
        assertNotNull(accountRes.getBody());
        String accountId = accountRes.getBody().getAccountId();
        assertEquals(3500.00, accountRes.getBody().getBalance().doubleValue(), 0.01);

        // Step 2: Deposit $1,500.00 into account -> New Balance $5,000.00
        AccountDepositRequest depositReq = new AccountDepositRequest();
        depositReq.setAmount(BigDecimal.valueOf(1500.00));
        depositReq.setDescription("Payroll direct deposit");

        ResponseEntity<AccountResponse> depositRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/accounts/" + accountId + "/deposit",
                depositReq, AccountResponse.class);
        assertEquals(HttpStatus.OK, depositRes.getStatusCode());
        assertEquals(5000.00, depositRes.getBody().getBalance().doubleValue(), 0.01);

        // Step 3: Issue Primary VISA Debit Card with $1,200.00 daily spend limit
        CardIssuanceRequest cardReq = new CardIssuanceRequest();
        cardReq.setAccountId(accountId);
        cardReq.setCardHolderName("Sophia Chen");
        cardReq.setDailyLimit(BigDecimal.valueOf(1200.00));

        ResponseEntity<CardResponse> cardRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/cards/issue", cardReq, CardResponse.class);
        assertEquals(HttpStatus.CREATED, cardRes.getStatusCode());
        CardResponse issuedCard = cardRes.getBody();
        assertNotNull(issuedCard);
        String cardId = issuedCard.getCardId();
        String cardToken = issuedCard.getCardToken();
        String cvv = issuedCard.getCvv();

        assertEquals(CardStatus.INACTIVE, issuedCard.getCardStatus());
        assertEquals(CardNetwork.VISA, issuedCard.getCardNetwork());
        assertTrue(issuedCard.getMaskedCardNumber().startsWith("4"));

        // Step 4: Transaction before activation must be declined (CARD_INACTIVE)
        TransactionAuthorizationRequest preActivateReq = new TransactionAuthorizationRequest();
        preActivateReq.setCardNumber(cardToken);
        preActivateReq.setPin("5821");
        preActivateReq.setAmount(BigDecimal.valueOf(100.00));
        preActivateReq.setCurrency("USD");
        preActivateReq.setChannel(TransactionChannel.POS);

        ResponseEntity<TransactionAuthorizationResponse> preActivateRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize", preActivateReq, TransactionAuthorizationResponse.class);
        assertEquals(HttpStatus.OK, preActivateRes.getStatusCode());
        assertEquals(TransactionStatus.DECLINED, preActivateRes.getBody().getStatus());
        assertEquals(DeclineReason.CARD_INACTIVE, preActivateRes.getBody().getDeclineReason());

        // Step 5: Activate Card with CVV and set PIN '5821'
        CardActivationRequest activateReq = new CardActivationRequest();
        activateReq.setCardId(cardId);
        activateReq.setCvv(cvv);
        activateReq.setPin("5821");

        ResponseEntity<CardResponse> activateRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/cards/" + cardId + "/activate", activateReq, CardResponse.class);
        assertEquals(HttpStatus.OK, activateRes.getStatusCode());
        assertEquals(CardStatus.ACTIVE, activateRes.getBody().getCardStatus());
        assertNull(activateRes.getBody().getCvv(), "CVV must be stripped after activation");

        // Step 6: POS In-Store Purchase at Nordstrom for $250.00 -> APPROVED
        TransactionAuthorizationRequest posReq = new TransactionAuthorizationRequest();
        posReq.setCardNumber(cardToken);
        posReq.setPin("5821");
        posReq.setAmount(BigDecimal.valueOf(250.00));
        posReq.setCurrency("USD");
        posReq.setMerchantName("Nordstrom Retail");
        posReq.setChannel(TransactionChannel.POS);
        posReq.setTransactionType(TransactionType.PURCHASE);

        ResponseEntity<TransactionAuthorizationResponse> posRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize", posReq, TransactionAuthorizationResponse.class);
        assertEquals(HttpStatus.OK, posRes.getStatusCode());
        assertEquals(TransactionStatus.APPROVED, posRes.getBody().getStatus());
        assertNotNull(posRes.getBody().getApprovalCode());
        assertEquals(950.00, posRes.getBody().getRemainingDailyLimit().doubleValue(), 0.01);

        // Verify Account Balance deducted to $4,750.00
        ResponseEntity<AccountResponse> bal1 = restTemplate.getForEntity(
                getBaseUrl() + "/api/v1/accounts/" + accountId, AccountResponse.class);
        assertEquals(4750.00, bal1.getBody().getBalance().doubleValue(), 0.01);

        // Step 7: Online E-Commerce Purchase at Amazon.com for $650.00 -> APPROVED
        TransactionAuthorizationRequest ecomReq = new TransactionAuthorizationRequest();
        ecomReq.setCardNumber(cardToken);
        ecomReq.setPin("5821");
        ecomReq.setAmount(BigDecimal.valueOf(650.00));
        ecomReq.setCurrency("USD");
        ecomReq.setMerchantName("Amazon.com Online");
        ecomReq.setChannel(TransactionChannel.ECOMMERCE);
        ecomReq.setTransactionType(TransactionType.PURCHASE);

        ResponseEntity<TransactionAuthorizationResponse> ecomRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize", ecomReq, TransactionAuthorizationResponse.class);
        assertEquals(HttpStatus.OK, ecomRes.getStatusCode());
        assertEquals(TransactionStatus.APPROVED, ecomRes.getBody().getStatus());
        assertEquals(300.00, ecomRes.getBody().getRemainingDailyLimit().doubleValue(), 0.01);

        // Balance: $4,750 - $650 = $4,100.00
        ResponseEntity<AccountResponse> bal2 = restTemplate.getForEntity(
                getBaseUrl() + "/api/v1/accounts/" + accountId, AccountResponse.class);
        assertEquals(4100.00, bal2.getBody().getBalance().doubleValue(), 0.01);

        // Step 8: ATM Withdrawal for $400.00 exceeds daily limit ($250 + $650 + $400 = $1300 > $1200) -> DECLINED
        TransactionAuthorizationRequest atmReq = new TransactionAuthorizationRequest();
        atmReq.setCardNumber(cardToken);
        atmReq.setPin("5821");
        atmReq.setAmount(BigDecimal.valueOf(400.00));
        atmReq.setCurrency("USD");
        atmReq.setMerchantName("Chase ATM Manhattan");
        atmReq.setChannel(TransactionChannel.ATM);
        atmReq.setTransactionType(TransactionType.ATM_WITHDRAWAL);

        ResponseEntity<TransactionAuthorizationResponse> atmRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize", atmReq, TransactionAuthorizationResponse.class);
        assertEquals(HttpStatus.OK, atmRes.getStatusCode());
        assertEquals(TransactionStatus.DECLINED, atmRes.getBody().getStatus());
        assertEquals(DeclineReason.EXCEEDS_DAILY_LIMIT, atmRes.getBody().getDeclineReason());

        // Balance remains unchanged at $4,100.00
        ResponseEntity<AccountResponse> bal3 = restTemplate.getForEntity(
                getBaseUrl() + "/api/v1/accounts/" + accountId, AccountResponse.class);
        assertEquals(4100.00, bal3.getBody().getBalance().doubleValue(), 0.01);

        // Step 9: Customer increases daily spend limit to $2,000.00 via controls
        CardControlsDto controls = new CardControlsDto();
        controls.setDailyLimit(BigDecimal.valueOf(2000.00));
        HttpHeaders putHeaders = new HttpHeaders();
        putHeaders.setContentType(MediaType.APPLICATION_JSON);
        restTemplate.exchange(
                getBaseUrl() + "/api/v1/cards/" + cardId + "/controls",
                HttpMethod.PUT,
                new HttpEntity<>(controls, putHeaders),
                CardResponse.class
        );

        // Step 10: Re-attempt ATM withdrawal for $400.00 -> Now APPROVED ($900 + $400 = $1300 <= $2000)
        ResponseEntity<TransactionAuthorizationResponse> atmRetryRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize", atmReq, TransactionAuthorizationResponse.class);
        assertEquals(HttpStatus.OK, atmRetryRes.getStatusCode());
        assertEquals(TransactionStatus.APPROVED, atmRetryRes.getBody().getStatus());
        assertEquals(700.00, atmRetryRes.getBody().getRemainingDailyLimit().doubleValue(), 0.01);

        // Balance: $4,100 - $400 = $3,700.00
        ResponseEntity<AccountResponse> bal4 = restTemplate.getForEntity(
                getBaseUrl() + "/api/v1/accounts/" + accountId, AccountResponse.class);
        assertEquals(3700.00, bal4.getBody().getBalance().doubleValue(), 0.01);

        // Step 11: Customer temporarily freezes card
        ResponseEntity<CardResponse> freezeRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/cards/" + cardId + "/freeze", null, CardResponse.class);
        assertEquals(HttpStatus.OK, freezeRes.getStatusCode());
        assertEquals(CardStatus.FROZEN, freezeRes.getBody().getCardStatus());

        // Step 12: Transaction while frozen -> DECLINED
        TransactionAuthorizationRequest frozenTxnReq = new TransactionAuthorizationRequest();
        frozenTxnReq.setCardNumber(cardToken);
        frozenTxnReq.setPin("5821");
        frozenTxnReq.setAmount(BigDecimal.valueOf(50.00));
        frozenTxnReq.setCurrency("USD");

        ResponseEntity<TransactionAuthorizationResponse> frozenTxnRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize", frozenTxnReq, TransactionAuthorizationResponse.class);
        assertEquals(HttpStatus.OK, frozenTxnRes.getStatusCode());
        assertEquals(TransactionStatus.DECLINED, frozenTxnRes.getBody().getStatus());
        assertEquals(DeclineReason.CARD_FROZEN, frozenTxnRes.getBody().getDeclineReason());

        // Step 13: Customer unfreezes card
        ResponseEntity<CardResponse> unfreezeRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/cards/" + cardId + "/unfreeze", null, CardResponse.class);
        assertEquals(HttpStatus.OK, unfreezeRes.getStatusCode());
        assertEquals(CardStatus.ACTIVE, unfreezeRes.getBody().getCardStatus());

        // Step 14: Purchase at Grocery Outlet for $85.00 -> APPROVED
        TransactionAuthorizationRequest groceryReq = new TransactionAuthorizationRequest();
        groceryReq.setCardNumber(cardToken);
        groceryReq.setPin("5821");
        groceryReq.setAmount(BigDecimal.valueOf(85.00));
        groceryReq.setCurrency("USD");
        groceryReq.setMerchantName("Grocery Outlet");

        ResponseEntity<TransactionAuthorizationResponse> groceryRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize", groceryReq, TransactionAuthorizationResponse.class);
        assertEquals(HttpStatus.OK, groceryRes.getStatusCode());
        assertEquals(TransactionStatus.APPROVED, groceryRes.getBody().getStatus());

        // Final Balance: $3,700 - $85 = $3,615.00
        ResponseEntity<AccountResponse> finalBal = restTemplate.getForEntity(
                getBaseUrl() + "/api/v1/accounts/" + accountId, AccountResponse.class);
        assertEquals(3615.00, finalBal.getBody().getBalance().doubleValue(), 0.01);

        // Step 15: Ledger Reconciliation - Verify transaction history
        ResponseEntity<List<TransactionResponse>> cardHistoryRes = restTemplate.exchange(
                getBaseUrl() + "/api/v1/transactions/card/" + cardId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.OK, cardHistoryRes.getStatusCode());
        List<TransactionResponse> cardTxns = cardHistoryRes.getBody();
        assertNotNull(cardTxns);
        assertTrue(cardTxns.size() >= 5, "Must record all transactions (approved and declined)");

        // Verify account history matches
        ResponseEntity<List<TransactionResponse>> accountHistoryRes = restTemplate.exchange(
                getBaseUrl() + "/api/v1/transactions/account/" + accountId,
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );
        assertEquals(HttpStatus.OK, accountHistoryRes.getStatusCode());
        assertNotNull(accountHistoryRes.getBody());
        assertEquals(cardTxns.size(), accountHistoryRes.getBody().size());
    }

    @Test
    @DisplayName("E2E Journey 2: Security & Brute-Force Lockout Defense (Marcus Vance)")
    void testSecurityLockoutDefenseJourney() {
        // Setup Account and Card
        AccountCreateRequest accReq = new AccountCreateRequest();
        accReq.setCustomerId("cust-marcus-" + System.nanoTime());
        accReq.setCustomerName("Marcus Vance");
        accReq.setAccountType(AccountType.CHECKING);
        accReq.setInitialDeposit(BigDecimal.valueOf(1000.00));
        accReq.setCurrency("USD");

        ResponseEntity<AccountResponse> accRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/accounts", accReq, AccountResponse.class);
        String accountId = accRes.getBody().getAccountId();

        CardIssuanceRequest crdReq = new CardIssuanceRequest();
        crdReq.setAccountId(accountId);
        crdReq.setCardHolderName("Marcus Vance");
        crdReq.setDailyLimit(BigDecimal.valueOf(500.00));

        ResponseEntity<CardResponse> crdRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/cards/issue", crdReq, CardResponse.class);
        String cardId = crdRes.getBody().getCardId();
        String cardToken = crdRes.getBody().getCardToken();
        String cvv = crdRes.getBody().getCvv();

        // Activate with PIN '9134'
        CardActivationRequest actReq = new CardActivationRequest();
        actReq.setCardId(cardId);
        actReq.setCvv(cvv);
        actReq.setPin("9134");
        restTemplate.postForEntity(getBaseUrl() + "/api/v1/cards/" + cardId + "/activate", actReq, CardResponse.class);

        // Attempt 1 with wrong PIN '0000' -> DECLINED (INVALID_PIN)
        TransactionAuthorizationRequest wrongPin1 = new TransactionAuthorizationRequest();
        wrongPin1.setCardNumber(cardToken);
        wrongPin1.setPin("0000");
        wrongPin1.setAmount(BigDecimal.valueOf(20.00));
        wrongPin1.setCurrency("USD");

        ResponseEntity<TransactionAuthorizationResponse> res1 = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize", wrongPin1, TransactionAuthorizationResponse.class);
        assertEquals(HttpStatus.OK, res1.getStatusCode());
        assertEquals(TransactionStatus.DECLINED, res1.getBody().getStatus());
        assertEquals(DeclineReason.INVALID_PIN, res1.getBody().getDeclineReason());

        // Attempt 2 with wrong PIN '1111' -> DECLINED (INVALID_PIN)
        TransactionAuthorizationRequest wrongPin2 = new TransactionAuthorizationRequest();
        wrongPin2.setCardNumber(cardToken);
        wrongPin2.setPin("1111");
        wrongPin2.setAmount(BigDecimal.valueOf(20.00));
        wrongPin2.setCurrency("USD");

        ResponseEntity<TransactionAuthorizationResponse> res2 = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize", wrongPin2, TransactionAuthorizationResponse.class);
        assertEquals(HttpStatus.OK, res2.getStatusCode());
        assertEquals(TransactionStatus.DECLINED, res2.getBody().getStatus());
        assertEquals(DeclineReason.INVALID_PIN, res2.getBody().getDeclineReason());

        // Attempt 3 with wrong PIN '2222' -> 3rd failure locks the card -> PIN_TRIES_EXCEEDED
        TransactionAuthorizationRequest wrongPin3 = new TransactionAuthorizationRequest();
        wrongPin3.setCardNumber(cardToken);
        wrongPin3.setPin("2222");
        wrongPin3.setAmount(BigDecimal.valueOf(20.00));
        wrongPin3.setCurrency("USD");

        ResponseEntity<TransactionAuthorizationResponse> res3 = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize", wrongPin3, TransactionAuthorizationResponse.class);
        assertEquals(HttpStatus.OK, res3.getStatusCode());
        assertEquals(TransactionStatus.DECLINED, res3.getBody().getStatus());
        assertEquals(DeclineReason.PIN_TRIES_EXCEEDED, res3.getBody().getDeclineReason());

        // Verify card status in DB is now BLOCKED
        ResponseEntity<CardResponse> cardStatusRes = restTemplate.getForEntity(
                getBaseUrl() + "/api/v1/cards/" + cardId, CardResponse.class);
        assertEquals(CardStatus.BLOCKED, cardStatusRes.getBody().getCardStatus());

        // Attempt 4 with the CORRECT PIN '9134' -> MUST STILL BE DECLINED (CARD_BLOCKED)
        TransactionAuthorizationRequest correctPinAfterLockout = new TransactionAuthorizationRequest();
        correctPinAfterLockout.setCardNumber(cardToken);
        correctPinAfterLockout.setPin("9134"); // The actual valid PIN
        correctPinAfterLockout.setAmount(BigDecimal.valueOf(20.00));
        correctPinAfterLockout.setCurrency("USD");

        ResponseEntity<TransactionAuthorizationResponse> res4 = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize", correctPinAfterLockout, TransactionAuthorizationResponse.class);
        assertEquals(HttpStatus.OK, res4.getStatusCode());
        assertEquals(TransactionStatus.DECLINED, res4.getBody().getStatus());
        assertEquals(DeclineReason.CARD_BLOCKED, res4.getBody().getDeclineReason());
    }

    @Test
    @DisplayName("E2E Journey 3: Multi-Account Portfolio Management (Alexander Wright)")
    void testMultiAccountPortfolioIsolationJourney() {
        // Checking Account ($1,000)
        AccountCreateRequest checkingReq = new AccountCreateRequest();
        checkingReq.setCustomerId("cust-alexander-" + System.nanoTime());
        checkingReq.setCustomerName("Alexander Wright");
        checkingReq.setAccountType(AccountType.CHECKING);
        checkingReq.setInitialDeposit(BigDecimal.valueOf(1000.00));
        checkingReq.setCurrency("USD");
        ResponseEntity<AccountResponse> checkingRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/accounts", checkingReq, AccountResponse.class);
        String checkingAccountId = checkingRes.getBody().getAccountId();

        // Savings Account ($2,000)
        AccountCreateRequest savingsReq = new AccountCreateRequest();
        savingsReq.setCustomerId("cust-alexander-" + System.nanoTime());
        savingsReq.setCustomerName("Alexander Wright");
        savingsReq.setAccountType(AccountType.SAVINGS);
        savingsReq.setInitialDeposit(BigDecimal.valueOf(2000.00));
        savingsReq.setCurrency("USD");
        ResponseEntity<AccountResponse> savingsRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/accounts", savingsReq, AccountResponse.class);
        String savingsAccountId = savingsRes.getBody().getAccountId();

        // Issue Card 1 on Checking Account
        CardIssuanceRequest card1Req = new CardIssuanceRequest();
        card1Req.setAccountId(checkingAccountId);
        card1Req.setCardHolderName("Alexander Wright");
        card1Req.setDailyLimit(BigDecimal.valueOf(500.00));
        ResponseEntity<CardResponse> card1Res = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/cards/issue", card1Req, CardResponse.class);
        String card1Id = card1Res.getBody().getCardId();
        String card1Token = card1Res.getBody().getCardToken();
        String card1Cvv = card1Res.getBody().getCvv();

        // Activate Card 1
        CardActivationRequest act1 = new CardActivationRequest();
        act1.setCardId(card1Id);
        act1.setCvv(card1Cvv);
        act1.setPin("1122");
        restTemplate.postForEntity(getBaseUrl() + "/api/v1/cards/" + card1Id + "/activate", act1, CardResponse.class);

        // Authorize $150 transaction on Card 1
        TransactionAuthorizationRequest txn1 = new TransactionAuthorizationRequest();
        txn1.setCardNumber(card1Token);
        txn1.setPin("1122");
        txn1.setAmount(BigDecimal.valueOf(150.00));
        txn1.setCurrency("USD");
        ResponseEntity<TransactionAuthorizationResponse> txn1Res = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize", txn1, TransactionAuthorizationResponse.class);
        assertEquals(TransactionStatus.APPROVED, txn1Res.getBody().getStatus());

        // Verify: Checking account debited to $850, Savings account UNTOUCHED at $2000
        ResponseEntity<AccountResponse> chkBal = restTemplate.getForEntity(
                getBaseUrl() + "/api/v1/accounts/" + checkingAccountId, AccountResponse.class);
        assertEquals(850.00, chkBal.getBody().getBalance().doubleValue(), 0.01);

        ResponseEntity<AccountResponse> savBal = restTemplate.getForEntity(
                getBaseUrl() + "/api/v1/accounts/" + savingsAccountId, AccountResponse.class);
        assertEquals(2000.00, savBal.getBody().getBalance().doubleValue(), 0.01);

        // Verify account isolation in card lists
        ResponseEntity<List<CardResponse>> checkingCards = restTemplate.exchange(
                getBaseUrl() + "/api/v1/cards/account/" + checkingAccountId,
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        assertEquals(1, checkingCards.getBody().size());
        assertEquals(card1Id, checkingCards.getBody().get(0).getCardId());

        ResponseEntity<List<CardResponse>> savingsCards = restTemplate.exchange(
                getBaseUrl() + "/api/v1/cards/account/" + savingsAccountId,
                HttpMethod.GET, null, new ParameterizedTypeReference<>() {});
        assertEquals(0, savingsCards.getBody().size());
    }

    @Test
    @DisplayName("E2E Journey 4: Zero-Balance Depletion & Top-Up Recovery")
    void testZeroBalanceDepletionAndTopUpRecovery() {
        // Account with $100
        AccountCreateRequest accReq = new AccountCreateRequest();
        accReq.setCustomerId("cust-depletion-" + System.nanoTime());
        accReq.setCustomerName("Depletion User");
        accReq.setAccountType(AccountType.CHECKING);
        accReq.setInitialDeposit(BigDecimal.valueOf(100.00));
        accReq.setCurrency("USD");
        ResponseEntity<AccountResponse> accRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/accounts", accReq, AccountResponse.class);
        String accountId = accRes.getBody().getAccountId();

        CardIssuanceRequest crdReq = new CardIssuanceRequest();
        crdReq.setAccountId(accountId);
        crdReq.setCardHolderName("Depletion User");
        crdReq.setDailyLimit(BigDecimal.valueOf(500.00));
        ResponseEntity<CardResponse> crdRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/cards/issue", crdReq, CardResponse.class);
        String cardId = crdRes.getBody().getCardId();
        String cardToken = crdRes.getBody().getCardToken();
        String cvv = crdRes.getBody().getCvv();

        CardActivationRequest actReq = new CardActivationRequest();
        actReq.setCardId(cardId);
        actReq.setCvv(cvv);
        actReq.setPin("4444");
        restTemplate.postForEntity(getBaseUrl() + "/api/v1/cards/" + cardId + "/activate", actReq, CardResponse.class);

        // Spend exact $100 -> balance becomes $0.00
        TransactionAuthorizationRequest drainReq = new TransactionAuthorizationRequest();
        drainReq.setCardNumber(cardToken);
        drainReq.setPin("4444");
        drainReq.setAmount(BigDecimal.valueOf(100.00));
        drainReq.setCurrency("USD");
        ResponseEntity<TransactionAuthorizationResponse> drainRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize", drainReq, TransactionAuthorizationResponse.class);
        assertEquals(TransactionStatus.APPROVED, drainRes.getBody().getStatus());

        ResponseEntity<AccountResponse> zeroBal = restTemplate.getForEntity(
                getBaseUrl() + "/api/v1/accounts/" + accountId, AccountResponse.class);
        assertEquals(0.00, zeroBal.getBody().getBalance().doubleValue(), 0.001);

        // Attempt $10 purchase on $0 balance -> DECLINED (INSUFFICIENT_FUNDS)
        TransactionAuthorizationRequest overspendReq = new TransactionAuthorizationRequest();
        overspendReq.setCardNumber(cardToken);
        overspendReq.setPin("4444");
        overspendReq.setAmount(BigDecimal.valueOf(10.00));
        overspendReq.setCurrency("USD");
        ResponseEntity<TransactionAuthorizationResponse> overspendRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize", overspendReq, TransactionAuthorizationResponse.class);
        assertEquals(TransactionStatus.DECLINED, overspendRes.getBody().getStatus());
        assertEquals(DeclineReason.INSUFFICIENT_FUNDS, overspendRes.getBody().getDeclineReason());

        // Top up account with $200
        AccountDepositRequest deposit = new AccountDepositRequest();
        deposit.setAmount(BigDecimal.valueOf(200.00));
        deposit.setDescription("Instant recovery top-up");
        restTemplate.postForEntity(getBaseUrl() + "/api/v1/accounts/" + accountId + "/deposit", deposit, AccountResponse.class);

        // Now transaction succeeds!
        ResponseEntity<TransactionAuthorizationResponse> recoveryRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize", overspendReq, TransactionAuthorizationResponse.class);
        assertEquals(TransactionStatus.APPROVED, recoveryRes.getBody().getStatus());

        ResponseEntity<AccountResponse> recoveredBal = restTemplate.getForEntity(
                getBaseUrl() + "/api/v1/accounts/" + accountId, AccountResponse.class);
        assertEquals(190.00, recoveredBal.getBody().getBalance().doubleValue(), 0.01);
    }
}
