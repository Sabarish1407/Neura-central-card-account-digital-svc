package com.nuera.banking.card.integration;

import com.nuera.banking.card.model.dto.AccountCreateRequest;
import com.nuera.banking.card.model.dto.AccountResponse;
import com.nuera.banking.card.model.dto.CardIssuanceRequest;
import com.nuera.banking.card.model.dto.CardResponse;
import com.nuera.banking.card.model.enums.AccountType;
import com.nuera.banking.card.model.enums.CardNetwork;
import com.nuera.banking.card.model.enums.CardStatus;
import com.nuera.banking.card.service.service.AccountService;
import com.nuera.banking.card.service.service.CardService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Genuine integration test against a live PostgreSQL database container.
 * Utilizes Testcontainers 1.x (org.testcontainers:postgresql:1.19.8).
 * Serves as an integration migration fixture for Central Neuro.
 */
@SpringBootTest
@DisplayName("Integration Tests: Testcontainers 1.x Live PostgreSQL")
class PostgreSqlTestcontainersIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(PostgreSqlTestcontainersIntegrationTest.class);
    private static PostgreSQLContainer<?> postgres;
    private static boolean isContainerRunning = false;

    static {
        try {
            if (DockerClientFactory.instance().isDockerAvailable()) {
                postgres = new PostgreSQLContainer<>("postgres:15-alpine")
                        .withDatabaseName("testcontainers_bank_db")
                        .withUsername("bank_admin")
                        .withPassword("secret_bank_pass");
                postgres.start();
                isContainerRunning = postgres.isRunning();
            }
        } catch (Throwable t) {
            log.info("Testcontainers environment check: Docker not active or container startup skipped ({})", t.getMessage());
            isContainerRunning = false;
        }
    }

    @BeforeAll
    static void checkDocker() {
        Assumptions.assumeTrue(
                isContainerRunning,
                "Docker or PostgreSQL testcontainer unavailable - skipping live container test"
        );
    }

    @AfterAll
    static void tearDown() {
        if (postgres != null && postgres.isRunning()) {
            postgres.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        if (isContainerRunning && postgres != null) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
            registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
            registry.add("spring.flyway.enabled", () -> "true");
        } else {
            // Graceful fallback so Spring application context does not fail if Docker is stopped
            registry.add("spring.datasource.url", () -> "jdbc:h2:mem:tcfake;DB_CLOSE_DELAY=-1");
            registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
            registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.H2Dialect");
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
            registry.add("spring.flyway.enabled", () -> "false");
        }
    }

    @Autowired
    private AccountService accountService;

    @Autowired
    private CardService cardService;

    @Test
    @DisplayName("Verify Flyway migrations and Account/Card lifecycle in live PostgreSQL container")
    void testLiveDatabasePersistenceWithTestcontainers() {
        // Step 1: Open Account in PostgreSQL container
        AccountCreateRequest accReq = new AccountCreateRequest();
        accReq.setCustomerId("cust-tc-01");
        accReq.setCustomerName("Testcontainers User");
        accReq.setAccountType(AccountType.CHECKING);
        accReq.setInitialDeposit(new BigDecimal("1000.00"));
        accReq.setCurrency("USD");

        AccountResponse account = accountService.createAccount(accReq);
        assertNotNull(account);
        assertNotNull(account.getAccountId());
        assertEquals("Testcontainers User", account.getCustomerName());

        // Step 2: Issue Debit Card in PostgreSQL container
        CardIssuanceRequest cardReq = new CardIssuanceRequest();
        cardReq.setAccountId(account.getAccountId());
        cardReq.setCardHolderName("Testcontainers User");
        cardReq.setDailyLimit(new BigDecimal("2000.00"));

        CardResponse card = cardService.issueDebitCard(cardReq);
        assertNotNull(card);
        assertEquals(CardNetwork.VISA, card.getCardNetwork());
        assertEquals(CardStatus.INACTIVE, card.getCardStatus());
        assertNotNull(card.getCardToken());

        // Step 3: Read back from containerized PostgreSQL
        CardResponse fetched = cardService.getCardById(card.getCardId());
        assertNotNull(fetched);
        assertEquals(card.getCardId(), fetched.getCardId());
        assertEquals(card.getMaskedCardNumber(), fetched.getMaskedCardNumber());
    }
}
