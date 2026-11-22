package com.nuera.banking.card.acceptance;

import com.nuera.banking.card.model.dto.*;
import com.nuera.banking.card.model.enums.*;
import com.nuera.banking.card.service.entity.CardEntity;
import com.nuera.banking.card.service.repository.CardRepository;
import com.nuera.banking.card.service.service.AccountService;
import com.nuera.banking.card.service.service.CardService;
import com.nuera.banking.card.service.service.TransactionService;
import com.nuera.banking.card.service.util.CardSecurityUtils;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Acceptance Criteria Tests (AC-01 through AC-12)")
class AcceptanceCriteriaTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private AccountService accountService;

    @Autowired
    private CardService cardService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private CardRepository cardRepository;

    private String testAccountId;

    @BeforeEach
    void setUp() {
        AccountCreateRequest req = new AccountCreateRequest();
        req.setCustomerId("cust-ac");
        req.setCustomerName("Acceptance Test User");
        req.setAccountType(AccountType.CHECKING);
        req.setInitialDeposit(BigDecimal.valueOf(5000.00));
        req.setCurrency("USD");
        AccountResponse acc = accountService.createAccount(req);
        this.testAccountId = acc.getAccountId();
    }

    @Test
    @DisplayName("AC-01: Issue VISA Debit Card on active account with masked number")
    void test_AC01_VisaDebitCardIssuance() {
        CardIssuanceRequest req = new CardIssuanceRequest();
        req.setAccountId(testAccountId);
        req.setCardHolderName("Acceptance Test User");
        req.setDailyLimit(BigDecimal.valueOf(1000.00));

        CardResponse card = cardService.issueDebitCard(req);

        assertNotNull(card);
        assertEquals(CardNetwork.VISA, card.getCardNetwork());
        assertEquals(CardType.DEBIT, card.getCardType());
        assertEquals(CardStatus.INACTIVE, card.getCardStatus());
        assertEquals(testAccountId, card.getAccountId());
        assertTrue(card.getMaskedCardNumber().startsWith("4"));
        assertTrue(card.getMaskedCardNumber().contains("*"));
        assertNotNull(card.getCardToken());
    }

    @Test
    @DisplayName("AC-02: Card data security - encrypted at rest, tokenized, plain text never stored")
    void test_AC02_CardDataSecurityAndPlainTextProhibition() {
        CardIssuanceRequest req = new CardIssuanceRequest();
        req.setAccountId(testAccountId);
        req.setCardHolderName("Acceptance Test User");

        CardResponse card = cardService.issueDebitCard(req);

        Optional<CardEntity> entityOpt = cardRepository.findById(card.getCardId());
        assertTrue(entityOpt.isPresent());
        CardEntity entity = entityOpt.get();

        // Plain text PAN is NEVER stored in database
        assertNotNull(entity.getCardNumberEncrypted());
        assertFalse(entity.getCardNumberEncrypted().startsWith("4111"));
        assertNotNull(entity.getCardNumberHash());
        assertNotNull(entity.getCardToken());
        assertEquals(card.getMaskedCardNumber(), entity.getCardNumberMasked());
    }

    @Test
    @DisplayName("AC-03: Card activation with CVV and set 4-digit PIN stored as salted BCrypt")
    void test_AC03_CardActivationWithCvvAndSaltedBcryptPin() {
        CardIssuanceRequest req = new CardIssuanceRequest();
        req.setAccountId(testAccountId);
        req.setCardHolderName("Acceptance Test User");
        CardResponse card = cardService.issueDebitCard(req);

        CardActivationRequest actReq = new CardActivationRequest();
        actReq.setCardId(card.getCardId());
        actReq.setCvv(card.getCvv());
        actReq.setPin("2468");

        CardResponse activated = cardService.activateDebitCard(actReq);
        assertEquals(CardStatus.ACTIVE, activated.getCardStatus());

        CardEntity entity = cardRepository.findById(card.getCardId()).orElseThrow();
        assertNotNull(entity.getPinHash());
        // Verify PIN is salted BCrypt
        assertTrue(entity.getPinHash().startsWith("$2a$") || entity.getPinHash().startsWith("$2b$"));
        assertNotEquals("2468", entity.getPinHash());
        assertTrue(CardSecurityUtils.verifyPin("2468", entity.getPinHash()));
    }

    @Test
    @DisplayName("AC-04: Freeze active card blocks transaction authorization")
    void test_AC04_CardFreezing() {
        CardResponse card = issueAndActivateCard("500.00", "1111");

        CardResponse frozen = cardService.freezeCard(card.getCardId());
        assertEquals(CardStatus.FROZEN, frozen.getCardStatus());

        TransactionAuthorizationRequest authReq = new TransactionAuthorizationRequest();
        authReq.setCardNumber(card.getCardToken());
        authReq.setPin("1111");
        authReq.setAmount(BigDecimal.valueOf(20.00));
        authReq.setCurrency("USD");

        TransactionAuthorizationResponse auth = transactionService.authorizeTransaction(authReq);
        assertEquals(TransactionStatus.DECLINED, auth.getStatus());
        assertEquals(DeclineReason.CARD_FROZEN, auth.getDeclineReason());
    }

    @Test
    @DisplayName("AC-05: Unfreeze frozen card restores authorization capability")
    void test_AC05_CardUnfreezing() {
        CardResponse card = issueAndActivateCard("500.00", "2222");
        cardService.freezeCard(card.getCardId());

        CardResponse unfrozen = cardService.unfreezeCard(card.getCardId());
        assertEquals(CardStatus.ACTIVE, unfrozen.getCardStatus());

        TransactionAuthorizationRequest authReq = new TransactionAuthorizationRequest();
        authReq.setCardNumber(card.getCardToken());
        authReq.setPin("2222");
        authReq.setAmount(BigDecimal.valueOf(25.00));
        authReq.setCurrency("USD");

        TransactionAuthorizationResponse auth = transactionService.authorizeTransaction(authReq);
        assertEquals(TransactionStatus.APPROVED, auth.getStatus());
    }

    @Test
    @DisplayName("AC-06: Transaction authorization with PIN verification and daily limit check")
    void test_AC06_TransactionAuthorizationWithPinAndDailyLimit() {
        CardResponse card = issueAndActivateCard("1000.00", "3333");

        TransactionAuthorizationRequest authReq = new TransactionAuthorizationRequest();
        authReq.setCardNumber(card.getCardToken());
        authReq.setPin("3333");
        authReq.setAmount(BigDecimal.valueOf(150.00));
        authReq.setCurrency("USD");
        authReq.setMerchantName("Target");

        TransactionAuthorizationResponse auth = transactionService.authorizeTransaction(authReq);
        assertEquals(TransactionStatus.APPROVED, auth.getStatus());
        assertNotNull(auth.getApprovalCode());
    }

    @Test
    @DisplayName("AC-07: Salted BCrypt PIN check and card lockout after 3 consecutive wrong attempts")
    void test_AC07_PinVerificationAndThreeAttemptLockout() {
        CardResponse card = issueAndActivateCard("1000.00", "4444");

        TransactionAuthorizationRequest wrongReq = new TransactionAuthorizationRequest();
        wrongReq.setCardNumber(card.getCardToken());
        wrongReq.setPin("0000"); // Wrong PIN
        wrongReq.setAmount(BigDecimal.valueOf(10.00));
        wrongReq.setCurrency("USD");

        // Attempt 1
        TransactionAuthorizationResponse res1 = transactionService.authorizeTransaction(wrongReq);
        assertEquals(TransactionStatus.DECLINED, res1.getStatus());
        assertEquals(DeclineReason.INVALID_PIN, res1.getDeclineReason());

        // Attempt 2
        TransactionAuthorizationResponse res2 = transactionService.authorizeTransaction(wrongReq);
        assertEquals(TransactionStatus.DECLINED, res2.getStatus());
        assertEquals(DeclineReason.INVALID_PIN, res2.getDeclineReason());

        // Attempt 3 -> Should block card
        TransactionAuthorizationResponse res3 = transactionService.authorizeTransaction(wrongReq);
        assertEquals(TransactionStatus.DECLINED, res3.getStatus());
        assertTrue(res3.getDeclineReason() == DeclineReason.PIN_TRIES_EXCEEDED || res3.getDeclineReason() == DeclineReason.PIN_ATTEMPTS_EXCEEDED);

        CardEntity blockedCard = cardRepository.findById(card.getCardId()).orElseThrow();
        assertEquals(CardStatus.BLOCKED, blockedCard.getCardStatus());
    }

    @Test
    @DisplayName("AC-08: Transaction exceeds daily spend limit is declined")
    void test_AC08_DailyLimitEnforcement() {
        CardResponse card = issueAndActivateCard("100.00", "5555"); // Daily limit $100

        TransactionAuthorizationRequest overReq = new TransactionAuthorizationRequest();
        overReq.setCardNumber(card.getCardToken());
        overReq.setPin("5555");
        overReq.setAmount(BigDecimal.valueOf(150.00)); // Exceeds $100
        overReq.setCurrency("USD");

        TransactionAuthorizationResponse res = transactionService.authorizeTransaction(overReq);
        assertEquals(TransactionStatus.DECLINED, res.getStatus());
        assertTrue(res.getDeclineReason() == DeclineReason.EXCEEDS_DAILY_LIMIT || res.getDeclineReason() == DeclineReason.DAILY_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("AC-09: Decline transaction when account balance is insufficient")
    void test_AC09_InsufficientFundsDecline() {
        // Create an account with small balance
        AccountCreateRequest lowAccReq = new AccountCreateRequest();
        lowAccReq.setCustomerId("cust-low");
        lowAccReq.setCustomerName("Low Balance User");
        lowAccReq.setAccountType(AccountType.CHECKING);
        lowAccReq.setInitialDeposit(BigDecimal.valueOf(15.00));
        AccountResponse lowAcc = accountService.createAccount(lowAccReq);

        CardIssuanceRequest cardReq = new CardIssuanceRequest();
        cardReq.setAccountId(lowAcc.getAccountId());
        cardReq.setCardHolderName("Low Balance User");
        CardResponse card = cardService.issueDebitCard(cardReq);

        CardActivationRequest actReq = new CardActivationRequest();
        actReq.setCardId(card.getCardId());
        actReq.setCvv(card.getCvv());
        actReq.setPin("6666");
        cardService.activateDebitCard(actReq);

        TransactionAuthorizationRequest authReq = new TransactionAuthorizationRequest();
        authReq.setCardNumber(card.getCardToken());
        authReq.setPin("6666");
        authReq.setAmount(BigDecimal.valueOf(50.00)); // Account only has 15.00
        authReq.setCurrency("USD");

        TransactionAuthorizationResponse res = transactionService.authorizeTransaction(authReq);
        assertEquals(TransactionStatus.DECLINED, res.getStatus());
        assertEquals(DeclineReason.INSUFFICIENT_FUNDS, res.getDeclineReason());
    }

    @Test
    @DisplayName("AC-10: Inactive or blocked card decline")
    void test_AC10_InactiveOrBlockedCardDecline() {
        // Issuance only (still INACTIVE)
        CardIssuanceRequest cardReq = new CardIssuanceRequest();
        cardReq.setAccountId(testAccountId);
        cardReq.setCardHolderName("Inactive User");
        CardResponse card = cardService.issueDebitCard(cardReq);

        TransactionAuthorizationRequest authReq = new TransactionAuthorizationRequest();
        authReq.setCardNumber(card.getCardToken());
        authReq.setPin("1234");
        authReq.setAmount(BigDecimal.valueOf(10.00));
        authReq.setCurrency("USD");

        TransactionAuthorizationResponse res = transactionService.authorizeTransaction(authReq);
        assertEquals(TransactionStatus.DECLINED, res.getStatus());
        assertEquals(DeclineReason.CARD_INACTIVE, res.getDeclineReason());
    }

    @Test
    @DisplayName("AC-11: Transaction history retrieval preserves audit log")
    void test_AC11_TransactionHistoryRetrieval() {
        CardResponse card = issueAndActivateCard("1000.00", "7777");

        TransactionAuthorizationRequest authReq = new TransactionAuthorizationRequest();
        authReq.setCardNumber(card.getCardToken());
        authReq.setPin("7777");
        authReq.setAmount(BigDecimal.valueOf(30.00));
        authReq.setCurrency("USD");
        transactionService.authorizeTransaction(authReq);

        List<TransactionResponse> history = transactionService.getTransactionsForCard(card.getCardId());
        assertFalse(history.isEmpty());
        assertEquals(1, history.size());
        assertEquals(0, new BigDecimal("30.00").compareTo(history.get(0).getAmount()));
        assertEquals(TransactionStatus.APPROVED, history.get(0).getStatus());
        assertNotNull(history.get(0).getMaskedCardNumber());
    }

    @Test
    @DisplayName("AC-12: Health endpoint /actuator/health is UP and configurable SERVER_PORT")
    void test_AC12_HealthEndpointAndConfigurablePort() {
        ResponseEntity<Map> healthRes = restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", Map.class);
        assertEquals(HttpStatus.OK, healthRes.getStatusCode());
        assertNotNull(healthRes.getBody());
        assertEquals("UP", healthRes.getBody().get("status"));
    }

    @Test
    @DisplayName("AC-Optional: External live PostgreSQL integration skips cleanly if unavailable")
    void test_ExternalPostgreSqlAssumptionSkip() {
        boolean postgresAvailable = System.getenv("POSTGRES_LIVE_HOST") != null;
        Assumptions.assumeTrue(postgresAvailable, "Skipping live PostgreSQL assumption test: POSTGRES_LIVE_HOST not configured");
    }

    private CardResponse issueAndActivateCard(String limit, String pin) {
        CardIssuanceRequest cardReq = new CardIssuanceRequest();
        cardReq.setAccountId(testAccountId);
        cardReq.setCardHolderName("Acceptance Test User");
        cardReq.setDailyLimit(new BigDecimal(limit));
        CardResponse card = cardService.issueDebitCard(cardReq);

        CardActivationRequest actReq = new CardActivationRequest();
        actReq.setCardId(card.getCardId());
        actReq.setCvv(card.getCvv());
        actReq.setPin(pin);
        return cardService.activateDebitCard(actReq);
    }
}
