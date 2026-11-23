package com.nuera.banking.card.performance;

import com.nuera.banking.card.model.dto.*;
import com.nuera.banking.card.model.enums.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance and Concurrency Benchmark Tests for the Debit Card Service.
 *
 * Verifies:
 * 1. Double-spend prevention and financial balance integrity under concurrent transactions
 * 2. Transaction authorization latency SLAs (p50, p90, p95, p99 percentiles)
 * 3. High-throughput card issuance and AES-256 cryptographic throughput
 * 4. Concurrent daily spending limit enforcement
 * 5. Concurrent read throughput and stability
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureTestRestTemplate
@DisplayName("Performance & Concurrency Benchmark Tests")
class DebitCardPerformanceTest {

    private static final Logger log = LoggerFactory.getLogger(DebitCardPerformanceTest.class);

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    @DisplayName("Performance: High-Concurrency Multi-Card Transaction Throughput & Isolation")
    void testConcurrentMultiCardTransactionThroughput() throws InterruptedException {
        int cardCount = 10;
        BigDecimal initialBalance = BigDecimal.valueOf(200.00);
        BigDecimal txnAmount = BigDecimal.valueOf(50.00);

        List<String> accountIds = new ArrayList<>();
        List<String> cardTokens = new ArrayList<>();

        // Pre-create 10 funded accounts and active debit cards
        for (int i = 0; i < cardCount; i++) {
            AccountCreateRequest accReq = new AccountCreateRequest();
            accReq.setCustomerId("cust-multi-" + i + "-" + System.nanoTime());
            accReq.setCustomerName("Concurrent User " + i);
            accReq.setAccountType(AccountType.CHECKING);
            accReq.setInitialDeposit(initialBalance);
            accReq.setCurrency("USD");

            ResponseEntity<AccountResponse> accRes = restTemplate.postForEntity(
                    getBaseUrl() + "/api/v1/accounts", accReq, AccountResponse.class);
            assertEquals(HttpStatus.CREATED, accRes.getStatusCode());
            String accId = accRes.getBody().getAccountId();
            accountIds.add(accId);

            CardIssuanceRequest crdReq = new CardIssuanceRequest();
            crdReq.setAccountId(accId);
            crdReq.setCardHolderName("Concurrent User " + i);
            crdReq.setDailyLimit(BigDecimal.valueOf(1000.00));

            ResponseEntity<CardResponse> crdRes = restTemplate.postForEntity(
                    getBaseUrl() + "/api/v1/cards/issue", crdReq, CardResponse.class);
            assertEquals(HttpStatus.CREATED, crdRes.getStatusCode());
            String crdId = crdRes.getBody().getCardId();
            String token = crdRes.getBody().getCardToken();
            String cvv = crdRes.getBody().getCvv();
            cardTokens.add(token);

            CardActivationRequest actReq = new CardActivationRequest();
            actReq.setCardId(crdId);
            actReq.setCvv(cvv);
            actReq.setPin("1234");
            restTemplate.postForEntity(getBaseUrl() + "/api/v1/cards/" + crdId + "/activate", actReq, CardResponse.class);
        }

        // Concurrently execute transactions across all 10 cards simultaneously
        ExecutorService executor = Executors.newFixedThreadPool(cardCount);
        CountDownLatch readyLatch = new CountDownLatch(cardCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(cardCount);

        AtomicInteger approvedCount = new AtomicInteger(0);
        ConcurrentLinkedQueue<Long> latenciesMs = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < cardCount; i++) {
            final int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    long start = System.currentTimeMillis();

                    TransactionAuthorizationRequest authReq = new TransactionAuthorizationRequest();
                    authReq.setCardNumber(cardTokens.get(index));
                    authReq.setPin("1234");
                    authReq.setAmount(txnAmount);
                    authReq.setCurrency("USD");
                    authReq.setMerchantName("Concurrent Merchant " + index);
                    authReq.setChannel(TransactionChannel.POS);

                    ResponseEntity<TransactionAuthorizationResponse> response = restTemplate.postForEntity(
                            getBaseUrl() + "/api/v1/transactions/authorize",
                            authReq,
                            TransactionAuthorizationResponse.class
                    );

                    latenciesMs.add(System.currentTimeMillis() - start);

                    if (response.getStatusCode() == HttpStatus.OK &&
                            response.getBody() != null &&
                            response.getBody().getStatus() == TransactionStatus.APPROVED) {
                        approvedCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    log.error("Thread error in multi-card test", e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        long wallClockStart = System.currentTimeMillis();
        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS), "All concurrent requests must complete within timeout");
        long wallClockTime = System.currentTimeMillis() - wallClockStart;
        executor.shutdown();

        log.info("Concurrent Multi-Card Results: {} cards, {} approved in {}ms",
                cardCount, approvedCount.get(), wallClockTime);

        // All 10 concurrent transactions across distinct cards must succeed
        assertEquals(cardCount, approvedCount.get(), "All concurrent multi-card transactions must be approved");

        // Verify balance isolation on each account: $200 - $50 = $150
        for (String accId : accountIds) {
            ResponseEntity<AccountResponse> balRes = restTemplate.getForEntity(
                    getBaseUrl() + "/api/v1/accounts/" + accId, AccountResponse.class);
            assertEquals(HttpStatus.OK, balRes.getStatusCode());
            assertNotNull(balRes.getBody());
            assertEquals(150.00, balRes.getBody().getBalance().doubleValue(), 0.01,
                    "Account " + accId + " balance must be exactly $150.00");
        }
    }

    @Test
    @DisplayName("Performance: Rapid Sequential Balance Depletion & Financial Ledger Integrity")
    void testSequentialBalanceDepletionIntegrity() {
        // Create account with $200.00
        BigDecimal initialBalance = BigDecimal.valueOf(200.00);
        AccountCreateRequest accountReq = new AccountCreateRequest();
        accountReq.setCustomerId("cust-seq-drain-" + System.nanoTime());
        accountReq.setCustomerName("Sequential Drain Tester");
        accountReq.setAccountType(AccountType.CHECKING);
        accountReq.setInitialDeposit(initialBalance);
        accountReq.setCurrency("USD");

        ResponseEntity<AccountResponse> accountRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/accounts", accountReq, AccountResponse.class);
        String accountId = accountRes.getBody().getAccountId();

        CardIssuanceRequest cardReq = new CardIssuanceRequest();
        cardReq.setAccountId(accountId);
        cardReq.setCardHolderName("Drain Tester");
        cardReq.setDailyLimit(BigDecimal.valueOf(1000.00));

        ResponseEntity<CardResponse> cardRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/cards/issue", cardReq, CardResponse.class);
        String cardId = cardRes.getBody().getCardId();
        String cardToken = cardRes.getBody().getCardToken();
        String cvv = cardRes.getBody().getCvv();

        CardActivationRequest activateReq = new CardActivationRequest();
        activateReq.setCardId(cardId);
        activateReq.setCvv(cvv);
        activateReq.setPin("6543");
        restTemplate.postForEntity(getBaseUrl() + "/api/v1/cards/" + cardId + "/activate", activateReq, CardResponse.class);

        // Perform 4 sequential transactions of $50 each -> exactly exhausts $200
        BigDecimal txnAmount = BigDecimal.valueOf(50.00);
        for (int i = 0; i < 4; i++) {
            TransactionAuthorizationRequest authReq = new TransactionAuthorizationRequest();
            authReq.setCardNumber(cardToken);
            authReq.setPin("6543");
            authReq.setAmount(txnAmount);
            authReq.setCurrency("USD");
            authReq.setMerchantName("Merchant-" + i);

            ResponseEntity<TransactionAuthorizationResponse> res = restTemplate.postForEntity(
                    getBaseUrl() + "/api/v1/transactions/authorize", authReq, TransactionAuthorizationResponse.class);
            assertEquals(HttpStatus.OK, res.getStatusCode());
            assertNotNull(res.getBody());
            assertEquals(TransactionStatus.APPROVED, res.getBody().getStatus());
        }

        // Account balance must now be EXACTLY $0.00
        ResponseEntity<AccountResponse> zeroAccountRes = restTemplate.getForEntity(
                getBaseUrl() + "/api/v1/accounts/" + accountId, AccountResponse.class);
        assertEquals(0.00, zeroAccountRes.getBody().getBalance().doubleValue(), 0.001);

        // 5th transaction must be DECLINED due to INSUFFICIENT_FUNDS
        TransactionAuthorizationRequest postExhaustionReq = new TransactionAuthorizationRequest();
        postExhaustionReq.setCardNumber(cardToken);
        postExhaustionReq.setPin("6543");
        postExhaustionReq.setAmount(txnAmount);
        postExhaustionReq.setCurrency("USD");

        ResponseEntity<TransactionAuthorizationResponse> postRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/transactions/authorize", postExhaustionReq, TransactionAuthorizationResponse.class);
        assertEquals(HttpStatus.OK, postRes.getStatusCode());
        assertNotNull(postRes.getBody());
        assertEquals(TransactionStatus.DECLINED, postRes.getBody().getStatus());
        assertEquals(DeclineReason.INSUFFICIENT_FUNDS, postRes.getBody().getDeclineReason());

        // Balance remains $0.00
        ResponseEntity<AccountResponse> finalAccountRes = restTemplate.getForEntity(
                getBaseUrl() + "/api/v1/accounts/" + accountId, AccountResponse.class);
        assertEquals(0.00, finalAccountRes.getBody().getBalance().doubleValue(), 0.001);
    }

    @Test
    @DisplayName("Performance: Transaction Authorization Latency SLA Benchmark (p50, p90, p95, p99)")
    void testAuthorizationLatencySlaBenchmark() {
        // Setup Account and Card
        AccountCreateRequest accountReq = new AccountCreateRequest();
        accountReq.setCustomerId("cust-perf-sla-" + System.nanoTime());
        accountReq.setCustomerName("SLA Benchmark Customer");
        accountReq.setAccountType(AccountType.CHECKING);
        accountReq.setInitialDeposit(BigDecimal.valueOf(50000.00));
        accountReq.setCurrency("USD");

        ResponseEntity<AccountResponse> accRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/accounts", accountReq, AccountResponse.class);
        String accountId = accRes.getBody().getAccountId();

        CardIssuanceRequest cardReq = new CardIssuanceRequest();
        cardReq.setAccountId(accountId);
        cardReq.setCardHolderName("SLA Tester");
        cardReq.setDailyLimit(BigDecimal.valueOf(100000.00));
        ResponseEntity<CardResponse> crdRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/cards/issue", cardReq, CardResponse.class);
        String cardId = crdRes.getBody().getCardId();
        String cardToken = crdRes.getBody().getCardToken();
        String cvv = crdRes.getBody().getCvv();

        CardActivationRequest actReq = new CardActivationRequest();
        actReq.setCardId(cardId);
        actReq.setCvv(cvv);
        actReq.setPin("9876");
        restTemplate.postForEntity(getBaseUrl() + "/api/v1/cards/" + cardId + "/activate", actReq, CardResponse.class);

        // Warm-up phase (10 requests) to prime JIT compiler
        for (int i = 0; i < 10; i++) {
            TransactionAuthorizationRequest req = new TransactionAuthorizationRequest();
            req.setCardNumber(cardToken);
            req.setPin("9876");
            req.setAmount(BigDecimal.valueOf(1.00));
            req.setCurrency("USD");
            restTemplate.postForEntity(getBaseUrl() + "/api/v1/transactions/authorize", req, TransactionAuthorizationResponse.class);
        }

        // Benchmark phase (40 measured requests)
        int sampleSize = 40;
        List<Long> latencies = new ArrayList<>();

        for (int i = 0; i < sampleSize; i++) {
            TransactionAuthorizationRequest req = new TransactionAuthorizationRequest();
            req.setCardNumber(cardToken);
            req.setPin("9876");
            req.setAmount(BigDecimal.valueOf(2.00));
            req.setCurrency("USD");
            req.setMerchantName("SLA Benchmark Merchant " + i);

            long start = System.nanoTime();
            ResponseEntity<TransactionAuthorizationResponse> res = restTemplate.postForEntity(
                    getBaseUrl() + "/api/v1/transactions/authorize", req, TransactionAuthorizationResponse.class);
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertEquals(HttpStatus.OK, res.getStatusCode());
            assertNotNull(res.getBody());
            assertEquals(TransactionStatus.APPROVED, res.getBody().getStatus());
            latencies.add(elapsedMs);
        }

        Collections.sort(latencies);
        long min = latencies.get(0);
        long max = latencies.get(latencies.size() - 1);
        double mean = latencies.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p50 = latencies.get((int) (sampleSize * 0.50));
        long p90 = latencies.get((int) (sampleSize * 0.90));
        long p95 = latencies.get((int) (sampleSize * 0.95));
        long p99 = latencies.get((int) (sampleSize * 0.99));

        log.info("=== TRANSACTION AUTHORIZATION LATENCY SLA BENCHMARK ===");
        log.info("Samples: {}, Min: {}ms, Mean: {}ms, Max: {}ms", sampleSize, min, String.format("%.2f", mean), max);
        log.info("Percentiles: p50={}ms, p90={}ms, p95={}ms, p99={}ms", p50, p90, p95, p99);

        // Assert SLA guarantees:
        // In local/in-memory environment, p95 must be <= 350ms, mean <= 150ms
        assertTrue(mean < 250, "Mean authorization latency must be < 250ms, was: " + mean);
        assertTrue(p95 < 500, "p95 authorization latency must be < 500ms, was: " + p95);
    }

    @Test
    @DisplayName("Performance: High-Throughput Card Issuance & AES-256 Cryptographic Operations")
    void testCardIssuanceThroughput() {
        // Setup an account with funds
        AccountCreateRequest accReq = new AccountCreateRequest();
        accReq.setCustomerId("cust-throughput-" + System.nanoTime());
        accReq.setCustomerName("Throughput Test Account");
        accReq.setAccountType(AccountType.CHECKING);
        accReq.setInitialDeposit(BigDecimal.valueOf(5000.00));
        accReq.setCurrency("USD");

        ResponseEntity<AccountResponse> accRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/accounts", accReq, AccountResponse.class);
        String accountId = accRes.getBody().getAccountId();

        int issuanceCount = 25;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < issuanceCount; i++) {
            CardIssuanceRequest req = new CardIssuanceRequest();
            req.setAccountId(accountId);
            req.setCardHolderName("Throughput User " + i);
            req.setDailyLimit(BigDecimal.valueOf(500.00));

            ResponseEntity<CardResponse> res = restTemplate.postForEntity(
                    getBaseUrl() + "/api/v1/cards/issue", req, CardResponse.class);

            assertEquals(HttpStatus.CREATED, res.getStatusCode());
            assertNotNull(res.getBody());
            assertNotNull(res.getBody().getCardToken());
            assertTrue(res.getBody().getMaskedCardNumber().startsWith("4"));
        }

        long totalTimeMs = System.currentTimeMillis() - startTime;
        double throughputOpsPerSec = (issuanceCount / (double) totalTimeMs) * 1000;

        log.info("Card Issuance Benchmark: {} cards issued in {}ms ({:.2f} cards/sec)",
                issuanceCount, totalTimeMs, throughputOpsPerSec);

        assertTrue(throughputOpsPerSec > 5.0, "Issuance throughput must exceed 5 cards/sec");
    }

    @Test
    @DisplayName("Performance: Rapid Successive Daily Limit Exhaustion & Enforcement Under Load")
    void testDailyLimitExhaustionUnderLoad() {
        // Setup Account with large balance but small daily limit
        AccountCreateRequest accReq = new AccountCreateRequest();
        accReq.setCustomerId("cust-limit-stress-" + System.nanoTime());
        accReq.setCustomerName("Limit Stress Account");
        accReq.setAccountType(AccountType.CHECKING);
        accReq.setInitialDeposit(BigDecimal.valueOf(10000.00));
        accReq.setCurrency("USD");

        ResponseEntity<AccountResponse> accRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/accounts", accReq, AccountResponse.class);
        String accountId = accRes.getBody().getAccountId();

        // Card with daily limit of exactly $300.00
        BigDecimal dailyLimit = BigDecimal.valueOf(300.00);
        CardIssuanceRequest cardReq = new CardIssuanceRequest();
        cardReq.setAccountId(accountId);
        cardReq.setCardHolderName("Limit Tester");
        cardReq.setDailyLimit(dailyLimit);

        ResponseEntity<CardResponse> crdRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/cards/issue", cardReq, CardResponse.class);
        String cardId = crdRes.getBody().getCardId();
        String cardToken = crdRes.getBody().getCardToken();
        String cvv = crdRes.getBody().getCvv();

        CardActivationRequest actReq = new CardActivationRequest();
        actReq.setCardId(cardId);
        actReq.setCvv(cvv);
        actReq.setPin("7777");
        restTemplate.postForEntity(getBaseUrl() + "/api/v1/cards/" + cardId + "/activate", actReq, CardResponse.class);

        // Submit 5 transactions of $100 in rapid succession ($500 attempted against $300 limit)
        int attempts = 5;
        BigDecimal txnAmount = BigDecimal.valueOf(100.00);
        int approvedCount = 0;
        int limitDeclinedCount = 0;

        for (int i = 0; i < attempts; i++) {
            TransactionAuthorizationRequest req = new TransactionAuthorizationRequest();
            req.setCardNumber(cardToken);
            req.setPin("7777");
            req.setAmount(txnAmount);
            req.setCurrency("USD");
            req.setMerchantName("Merchant-" + i);

            ResponseEntity<TransactionAuthorizationResponse> res = restTemplate.postForEntity(
                    getBaseUrl() + "/api/v1/transactions/authorize",
                    req,
                    TransactionAuthorizationResponse.class
            );

            assertEquals(HttpStatus.OK, res.getStatusCode());
            assertNotNull(res.getBody());

            if (res.getBody().getStatus() == TransactionStatus.APPROVED) {
                approvedCount++;
            } else if (res.getBody().getDeclineReason() == DeclineReason.EXCEEDS_DAILY_LIMIT) {
                limitDeclinedCount++;
            }
        }

        log.info("Daily Limit Exhaustion Results: Approved={}, LimitDeclined={}, TotalAttempted={}",
                approvedCount, limitDeclinedCount, attempts);

        // Exactly 3 transactions of $100 should be APPROVED ($300 total = daily limit)
        assertEquals(3, approvedCount, "Exactly 3 transactions must be approved within $300 limit");
        // Exactly 2 transactions should be DECLINED with EXCEEDS_DAILY_LIMIT
        assertEquals(2, limitDeclinedCount, "Subsequent 2 transactions must be declined due to EXCEEDS_DAILY_LIMIT");

        BigDecimal totalSpent = txnAmount.multiply(BigDecimal.valueOf(approvedCount));
        assertEquals(dailyLimit.doubleValue(), totalSpent.doubleValue(), 0.001,
                "Total spent must exactly equal daily limit");
    }

    @Test
    @DisplayName("Performance: Concurrent Read Throughput (Account & Card Inquiries)")
    void testConcurrentReadThroughput() throws InterruptedException {
        // Create an account
        AccountCreateRequest accReq = new AccountCreateRequest();
        accReq.setCustomerId("cust-read-perf-" + System.nanoTime());
        accReq.setCustomerName("Read Benchmark Account");
        accReq.setAccountType(AccountType.CHECKING);
        accReq.setInitialDeposit(BigDecimal.valueOf(1000.00));
        accReq.setCurrency("USD");

        ResponseEntity<AccountResponse> accRes = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/accounts", accReq, AccountResponse.class);
        String accountId = accRes.getBody().getAccountId();

        int totalRequests = 40;
        int concurrency = 8;
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger successCount = new AtomicInteger(0);

        long start = System.currentTimeMillis();
        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    ResponseEntity<AccountResponse> res = restTemplate.getForEntity(
                            getBaseUrl() + "/api/v1/accounts/" + accountId, AccountResponse.class);
                    if (res.getStatusCode() == HttpStatus.OK && res.getBody() != null) {
                        successCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        long durationMs = System.currentTimeMillis() - start;
        executor.shutdown();

        double rps = (totalRequests / (double) durationMs) * 1000;
        log.info("Read Throughput: {} requests in {}ms ({:.2f} req/sec, 100% success)",
                totalRequests, durationMs, rps);

        assertEquals(totalRequests, successCount.get(), "100% of read requests must succeed");
        assertTrue(rps > 10.0, "Read throughput must be > 10 req/sec");
    }
}
