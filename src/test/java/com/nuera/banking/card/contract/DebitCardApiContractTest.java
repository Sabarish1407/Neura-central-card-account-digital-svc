package com.nuera.banking.card.contract;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.nuera.banking.card.model.dto.*;
import com.nuera.banking.card.model.enums.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * API Contract Tests for the Debit Card Digital Banking Service.
 *
 * Validates that all REST endpoints strictly adhere to their defined API contracts:
 * - JSON response schema structure and required fields
 * - Data types, formats (UUID prefixes, ISO-8601 timestamps, regex patterns)
 * - HTTP status codes and Content-Type headers
 * - Security contract: sensitive data omission (no raw PAN, CVV null after issuance, no plain text PIN)
 * - Standardized ErrorResponse schema on validation (400) and not found (404) errors
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@DisplayName("API Contract Tests: Request & Response Schema Verification")
class DebitCardApiContractTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    private String testAccountId;
    private String testCardId;
    private String testCardToken;
    private String testCvv;

    @BeforeEach
    void setUpFixtures() {
        // Create an account for contract tests
        AccountCreateRequest accountReq = new AccountCreateRequest();
        accountReq.setCustomerId("cust-contract-" + System.nanoTime());
        accountReq.setCustomerName("Contract Test User");
        accountReq.setAccountType(AccountType.CHECKING);
        accountReq.setInitialDeposit(BigDecimal.valueOf(2000.00));
        accountReq.setCurrency("USD");

        ResponseEntity<AccountResponse> accountRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/accounts",
                accountReq,
                AccountResponse.class
        );
        assertEquals(HttpStatus.CREATED, accountRes.getStatusCode());
        assertNotNull(accountRes.getBody());
        testAccountId = accountRes.getBody().getAccountId();

        // Issue a card
        CardIssuanceRequest cardReq = new CardIssuanceRequest();
        cardReq.setAccountId(testAccountId);
        cardReq.setCardHolderName("Contract Test User");
        cardReq.setDailyLimit(BigDecimal.valueOf(1000.00));

        ResponseEntity<CardResponse> cardRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/cards/issue",
                cardReq,
                CardResponse.class
        );
        assertEquals(HttpStatus.CREATED, cardRes.getStatusCode());
        assertNotNull(cardRes.getBody());
        testCardId = cardRes.getBody().getCardId();
        testCardToken = cardRes.getBody().getCardToken();
        testCvv = cardRes.getBody().getCvv();
    }

    @Nested
    @DisplayName("1. Accounts API Contract Tests")
    class AccountsContractTests {

        @Test
        @DisplayName("POST /api/v1/accounts - Contract: 201 Created with complete AccountResponse schema")
        void testCreateAccountContract() throws Exception {
            AccountCreateRequest req = new AccountCreateRequest();
            req.setCustomerId("cust-schema-" + System.nanoTime());
            req.setCustomerName("Schema Buyer");
            req.setAccountType(AccountType.CHECKING);
            req.setInitialDeposit(BigDecimal.valueOf(500.00));
            req.setCurrency("USD");

            ResponseEntity<String> response = restTemplate.postForEntity(
                    getBaseUrl() + "/api/v1/accounts",
                    req,
                    String.class
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            assertTrue(response.getHeaders().getContentType().toString().contains(MediaType.APPLICATION_JSON_VALUE));

            JsonNode root = objectMapper.readTree(response.getBody());

            // Schema field presence and format checks
            assertTrue(root.hasNonNull("accountId"), "accountId must be present");
            assertTrue(root.get("accountId").asString().startsWith("acc-"), "accountId must follow 'acc-*' format");
            assertTrue(root.hasNonNull("customerId"), "customerId must be present");
            assertTrue(root.hasNonNull("customerName"), "customerName must be present");
            assertTrue(root.hasNonNull("accountType"), "accountType must be present");
            assertEquals("CHECKING", root.get("accountType").asString());
            assertTrue(root.hasNonNull("currency"), "currency must be present");
            assertEquals("USD", root.get("currency").asString());
            assertTrue(root.hasNonNull("balance"), "balance must be present");
            assertTrue(root.get("balance").isNumber(), "balance must be a number");
            assertTrue(root.hasNonNull("availableBalance"), "availableBalance must be present");
            assertTrue(root.hasNonNull("status"), "status must be present");
            assertEquals("ACTIVE", root.get("status").asString());
            assertTrue(root.hasNonNull("createdAt"), "createdAt timestamp must be present");
        }

        @Test
        @DisplayName("GET /api/v1/accounts/{accountId} - Contract: 200 OK with valid schema")
        void testGetAccountContract() throws Exception {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    getBaseUrl() + "/api/v1/accounts/" + testAccountId,
                    String.class
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertTrue(response.getHeaders().getContentType().toString().contains(MediaType.APPLICATION_JSON_VALUE));

            JsonNode root = objectMapper.readTree(response.getBody());
            assertEquals(testAccountId, root.get("accountId").asString());
            assertEquals("ACTIVE", root.get("status").asString());
            assertEquals("USD", root.get("currency").asString());
            assertTrue(root.get("balance").asDouble() >= 0);
        }

        @Test
        @DisplayName("POST /api/v1/accounts/{accountId}/deposit - Contract: 200 OK with updated balance")
        void testDepositFundsContract() throws Exception {
            AccountDepositRequest depositReq = new AccountDepositRequest();
            depositReq.setAmount(BigDecimal.valueOf(250.00));
            depositReq.setDescription("Contract Test Payroll Deposit");

            ResponseEntity<String> response = restTemplate.postForEntity(
                    getBaseUrl() + "/api/v1/accounts/" + testAccountId + "/deposit",
                    depositReq,
                    String.class
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());
            JsonNode root = objectMapper.readTree(response.getBody());
            assertEquals(testAccountId, root.get("accountId").asString());
            assertEquals(2250.00, root.get("balance").asDouble(), 0.01);
            assertEquals(2250.00, root.get("availableBalance").asDouble(), 0.01);
        }
    }

    @Nested
    @DisplayName("2. Cards API Contract Tests")
    class CardsContractTests {

        @Test
        @DisplayName("POST /api/v1/cards/issue - Contract: 201 Created with CardResponse schema & one-time CVV")
        void testIssueCardContract() throws Exception {
            CardIssuanceRequest req = new CardIssuanceRequest();
            req.setAccountId(testAccountId);
            req.setCardHolderName("Contract Issuance Test");
            req.setDailyLimit(BigDecimal.valueOf(750.00));

            ResponseEntity<String> response = restTemplate.postForEntity(
                    getBaseUrl() + "/api/v1/cards/issue",
                    req,
                    String.class
            );

            assertEquals(HttpStatus.CREATED, response.getStatusCode());
            JsonNode root = objectMapper.readTree(response.getBody());

            // Contract schema validation
            assertTrue(root.hasNonNull("cardId"));
            assertTrue(root.get("cardId").asString().startsWith("crd-"));
            assertEquals(testAccountId, root.get("accountId").asString());
            assertTrue("Contract Issuance Test".equalsIgnoreCase(root.get("cardHolderName").asString()),
                    "cardHolderName must match input (case-insensitive embossed representation)");

            // Masked card number contract: 4xxx **** **** xxxx (PCI-DSS compliant masked representation)
            String masked = root.get("maskedCardNumber").asString();
            assertTrue(masked.matches("^4\\d{3} \\*{4} \\*{4} \\d{4}$"),
                    "Masked card number must follow format '4xxx **** **** xxxx', got: " + masked);

            // Card token contract
            assertTrue(root.hasNonNull("cardToken"));
            assertTrue(root.get("cardToken").asString().startsWith("tok-"));

            // Expiry date format MM/yy
            String expiry = root.get("expiryDate").asString();
            assertTrue(expiry.matches("^\\d{2}/\\d{2}$"),
                    "Expiry date must match MM/yy format, got: " + expiry);

            // Security: CVV returned ONCE upon issuance
            assertTrue(root.hasNonNull("cvv"));
            assertTrue(root.get("cvv").asString().matches("^\\d{3}$"), "CVV must be 3 digits");

            // Enum contracts
            assertEquals("VISA", root.get("cardNetwork").asString());
            assertEquals("DEBIT", root.get("cardType").asString());
            assertEquals("INACTIVE", root.get("cardStatus").asString());
            assertEquals(750.00, root.get("dailyLimit").asDouble(), 0.01);
        }

        @Test
        @DisplayName("POST /api/v1/cards/{cardId}/activate - Contract: 200 OK & CVV/PIN are NEVER returned")
        void testActivateCardContract() throws Exception {
            CardActivationRequest req = new CardActivationRequest();
            req.setCardId(testCardId);
            req.setCvv(testCvv);
            req.setPin("4321");

            ResponseEntity<String> response = restTemplate.postForEntity(
                    getBaseUrl() + "/api/v1/cards/" + testCardId + "/activate",
                    req,
                    String.class
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());
            JsonNode root = objectMapper.readTree(response.getBody());

            assertEquals("ACTIVE", root.get("cardStatus").asString());

            // Strict Security Contract: CVV and PIN must NEVER be exposed after activation
            assertTrue(root.get("cvv") == null || root.get("cvv").isNull(),
                    "CVV must be null after card activation");
            assertFalse(root.has("pin"), "PIN field must never exist in CardResponse");
        }

        @Test
        @DisplayName("GET /api/v1/cards/{cardId} - Contract: 200 OK & Zero sensitive data leakage")
        void testGetCardByIdContract() throws Exception {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    getBaseUrl() + "/api/v1/cards/" + testCardId,
                    String.class
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());
            JsonNode root = objectMapper.readTree(response.getBody());

            assertEquals(testCardId, root.get("cardId").asString());
            assertTrue(root.hasNonNull("maskedCardNumber"));
            assertTrue(root.get("cvv") == null || root.get("cvv").isNull(),
                    "CVV must be null on GET card detail");
            assertFalse(root.has("pin"), "PIN must never be present");
            assertFalse(root.has("encryptedCardNumber"), "Internal encrypted PAN must never be exposed");
            assertFalse(root.has("cardNumberHash"), "Internal hash must never be exposed");
        }

        @Test
        @DisplayName("GET /api/v1/cards/account/{accountId} - Contract: 200 OK with JSON Array of CardResponse")
        void testGetCardsByAccountContract() throws Exception {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    getBaseUrl() + "/api/v1/cards/account/" + testAccountId,
                    String.class
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());
            JsonNode root = objectMapper.readTree(response.getBody());

            assertTrue(root.isArray(), "Response must be a JSON array");
            assertTrue(root.size() >= 1, "Should contain at least the issued test card");
            assertEquals(testCardId, root.get(0).get("cardId").asString());
        }

        @Test
        @DisplayName("POST /api/v1/cards/{cardId}/freeze and /unfreeze - Contract: 200 OK with status transition")
        void testFreezeAndUnfreezeContract() throws Exception {
            // Freeze
            ResponseEntity<String> freezeRes = restTemplate.postForEntity(
                    getBaseUrl() + "/api/v1/cards/" + testCardId + "/freeze",
                    null,
                    String.class
            );
            assertEquals(HttpStatus.OK, freezeRes.getStatusCode());
            JsonNode freezeNode = objectMapper.readTree(freezeRes.getBody());
            assertEquals("FROZEN", freezeNode.get("cardStatus").asString());

            // Unfreeze
            ResponseEntity<String> unfreezeRes = restTemplate.postForEntity(
                    getBaseUrl() + "/api/v1/cards/" + testCardId + "/unfreeze",
                    null,
                    String.class
            );
            assertEquals(HttpStatus.OK, unfreezeRes.getStatusCode());
            JsonNode unfreezeNode = objectMapper.readTree(unfreezeRes.getBody());
            assertEquals("ACTIVE", unfreezeNode.get("cardStatus").asString());
        }

        @Test
        @DisplayName("PUT /api/v1/cards/{cardId}/controls - Contract: 200 OK with updated daily limit")
        void testUpdateControlsContract() throws Exception {
            CardControlsDto controls = new CardControlsDto();
            controls.setDailyLimit(BigDecimal.valueOf(1500.00));

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<CardControlsDto> entity = new HttpEntity<>(controls, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    getBaseUrl() + "/api/v1/cards/" + testCardId + "/controls",
                    HttpMethod.PUT,
                    entity,
                    String.class
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());
            JsonNode root = objectMapper.readTree(response.getBody());
            assertEquals(1500.00, root.get("dailyLimit").asDouble(), 0.01);
        }
    }

    @Nested
    @DisplayName("3. Transactions API Contract Tests")
    class TransactionsContractTests {

        private void activateTestCard() {
            CardActivationRequest req = new CardActivationRequest();
            req.setCardId(testCardId);
            req.setCvv(testCvv);
            req.setPin("1234");
            restTemplate.postForEntity(getBaseUrl() + "/api/v1/cards/" + testCardId + "/activate", req, CardResponse.class);
        }

        @Test
        @DisplayName("POST /api/v1/transactions/authorize - Contract: APPROVED Response Schema")
        void testAuthorizeTransactionApprovedContract() throws Exception {
            activateTestCard();

            TransactionAuthorizationRequest req = new TransactionAuthorizationRequest();
            req.setCardNumber(testCardToken);
            req.setPin("1234");
            req.setAmount(BigDecimal.valueOf(50.00));
            req.setCurrency("USD");
            req.setMerchantName("Target Store");
            req.setChannel(TransactionChannel.POS);
            req.setTransactionType(TransactionType.PURCHASE);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    getBaseUrl() + "/api/v1/transactions/authorize",
                    req,
                    String.class
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());
            JsonNode root = objectMapper.readTree(response.getBody());

            assertEquals("APPROVED", root.get("status").asString());
            assertTrue(root.hasNonNull("approvalCode"), "approvalCode must be present on APPROVED");
            assertTrue(root.get("approvalCode").asString().startsWith("AUTH"),
                    "approvalCode must start with 'AUTH', got: " + root.get("approvalCode").asString());
            assertTrue(root.hasNonNull("transactionId"));
            assertTrue(root.get("transactionId").asString().startsWith("txn-"));
            assertEquals(50.00, root.get("amount").asDouble(), 0.01);
            assertEquals("USD", root.get("currency").asString());
            assertTrue(root.hasNonNull("remainingDailyLimit"));
            assertTrue(root.get("remainingDailyLimit").isNumber());
        }

        @Test
        @DisplayName("POST /api/v1/transactions/authorize - Contract: DECLINED Response Schema")
        void testAuthorizeTransactionDeclinedContract() throws Exception {
            activateTestCard();

            TransactionAuthorizationRequest req = new TransactionAuthorizationRequest();
            req.setCardNumber(testCardToken);
            req.setPin("9999"); // Wrong PIN
            req.setAmount(BigDecimal.valueOf(50.00));
            req.setCurrency("USD");
            req.setMerchantName("Target Store");

            ResponseEntity<String> response = restTemplate.postForEntity(
                    getBaseUrl() + "/api/v1/transactions/authorize",
                    req,
                    String.class
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());
            JsonNode root = objectMapper.readTree(response.getBody());

            assertEquals("DECLINED", root.get("status").asString());
            assertTrue(root.get("approvalCode") == null || root.get("approvalCode").isNull(),
                    "approvalCode must be null on DECLINED");
            assertTrue(root.hasNonNull("declineReason"));
            assertEquals("INVALID_PIN", root.get("declineReason").asString());
            assertTrue(root.hasNonNull("transactionId"));
        }

        @Test
        @DisplayName("GET /api/v1/transactions/card/{cardId} - Contract: 200 OK with List of TransactionResponse")
        void testGetTransactionsForCardContract() throws Exception {
            activateTestCard();

            // Authorize a transaction so history is populated
            TransactionAuthorizationRequest req = new TransactionAuthorizationRequest();
            req.setCardNumber(testCardToken);
            req.setPin("1234");
            req.setAmount(BigDecimal.valueOf(30.00));
            req.setCurrency("USD");
            restTemplate.postForEntity(getBaseUrl() + "/api/v1/transactions/authorize", req, TransactionAuthorizationResponse.class);

            ResponseEntity<String> response = restTemplate.getForEntity(
                    getBaseUrl() + "/api/v1/transactions/card/" + testCardId,
                    String.class
            );

            assertEquals(HttpStatus.OK, response.getStatusCode());
            JsonNode root = objectMapper.readTree(response.getBody());

            assertTrue(root.isArray());
            assertTrue(root.size() >= 1);
            JsonNode first = root.get(0);
            assertTrue(first.hasNonNull("transactionId"));
            assertTrue(first.hasNonNull("referenceNumber"));
            assertTrue(first.hasNonNull("amount"));
            assertTrue(first.hasNonNull("currency"));
            assertTrue(first.hasNonNull("status"));
            assertTrue(first.hasNonNull("timestamp"));
        }
    }

    @Nested
    @DisplayName("4. Standard ErrorResponse Contract Tests")
    class ErrorResponseContractTests {

        @Test
        @DisplayName("HTTP 400 Bad Request - Contract: Standard ErrorResponse with details list")
        void testValidationFailureContract() throws Exception {
            // Missing customerId and invalid currency length
            AccountCreateRequest invalidReq = new AccountCreateRequest();
            invalidReq.setCustomerId(""); // Blank
            invalidReq.setCurrency("TOOLONG123"); // Exceeds 3 letters

            ResponseEntity<String> response = restTemplate.postForEntity(
                    getBaseUrl() + "/api/v1/accounts",
                    invalidReq,
                    String.class
            );

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            JsonNode root = objectMapper.readTree(response.getBody());

            // Standard Error Contract verification
            assertEquals(400, root.get("status").asInt());
            assertEquals("Validation Failed", root.get("error").asString());
            assertTrue(root.hasNonNull("message"));
            assertEquals("/api/v1/accounts", root.get("path").asString());
            assertTrue(root.hasNonNull("timestamp"));

            // Details field with individual validation errors
            assertTrue(root.hasNonNull("details"));
            assertTrue(root.get("details").isArray());
            assertTrue(root.get("details").size() >= 1);

            // Sanitization: No SQL or stack traces
            assertFalse(root.toString().toLowerCase().contains("sql"));
            assertFalse(root.toString().toLowerCase().contains("exception"));
        }

        @Test
        @DisplayName("HTTP 404 Not Found - Contract: Standard ErrorResponse on non-existent resource")
        void testResourceNotFoundContract() throws Exception {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    getBaseUrl() + "/api/v1/accounts/acc-non-existent-99999",
                    String.class
            );

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            JsonNode root = objectMapper.readTree(response.getBody());

            assertEquals(404, root.get("status").asInt());
            assertEquals("Not Found", root.get("error").asString());
            assertTrue(root.hasNonNull("message"));
            assertEquals("/api/v1/accounts/acc-non-existent-99999", root.get("path").asString());
            assertTrue(root.hasNonNull("timestamp"));
        }
    }
}
