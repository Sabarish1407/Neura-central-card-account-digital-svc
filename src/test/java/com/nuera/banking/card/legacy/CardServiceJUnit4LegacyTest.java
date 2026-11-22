package com.nuera.banking.card.legacy;

import com.nuera.banking.card.model.dto.CardActivationRequest;
import com.nuera.banking.card.model.dto.CardIssuanceRequest;
import com.nuera.banking.card.model.dto.CardResponse;
import com.nuera.banking.card.model.enums.AccountStatus;
import com.nuera.banking.card.model.enums.AccountType;
import com.nuera.banking.card.model.enums.CardNetwork;
import com.nuera.banking.card.model.enums.CardStatus;
import com.nuera.banking.card.service.entity.AccountEntity;
import com.nuera.banking.card.service.entity.CardEntity;
import com.nuera.banking.card.service.exception.BankingException;
import com.nuera.banking.card.service.repository.AccountRepository;
import com.nuera.banking.card.service.repository.CardRepository;
import com.nuera.banking.card.service.service.CardService;
import com.nuera.banking.card.service.service.EncryptionService;
import com.nuera.banking.card.service.util.CardSecurityUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Pure JUnit 4 + Mockito 3 test fixture for CardService.
 * Explicitly exercises legacy JUnit 4 lifecycle annotations:
 * - @RunWith(MockitoJUnitRunner.class)
 * - @Rule ExpectedException
 * - @BeforeClass / @AfterClass
 * - @Before / @After
 * - @Test
 * - org.junit.Assert
 * Designed for OpenRewrite automated JUnit 4 -> JUnit 5 & Mockito 3 -> 5 migration testing.
 */
@RunWith(MockitoJUnitRunner.class)
public class CardServiceJUnit4LegacyTest {

    private static String suiteContextId;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock
    private CardRepository cardRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private EncryptionService encryptionService;

    @InjectMocks
    private CardService cardService;

    private AccountEntity testAccount;

    @BeforeClass
    public static void setUpBeforeClass() {
        suiteContextId = "LEGACY-CARD-TEST-" + System.currentTimeMillis();
    }

    @AfterClass
    public static void tearDownAfterClass() {
        suiteContextId = null;
    }

    @Before
    public void setUp() {
        testAccount = new AccountEntity(
                "acc-legacy-01",
                "1000998877",
                "cust-legacy-1",
                "Legacy Test User",
                AccountType.CHECKING,
                new BigDecimal("2500.00"),
                "USD",
                AccountStatus.ACTIVE
        );
    }

    @After
    public void tearDown() {
        testAccount = null;
    }

    @Test
    public void testIssueDebitCard_Success() {
        when(accountRepository.findById("acc-legacy-01")).thenReturn(Optional.of(testAccount));
        when(encryptionService.encrypt(any())).thenReturn("aes256_mock_cipher");
        when(cardRepository.save(any(CardEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CardIssuanceRequest request = new CardIssuanceRequest();
        request.setAccountId("acc-legacy-01");
        request.setCardHolderName("Legacy Test User");
        request.setDailyLimit(new BigDecimal("1500.00"));

        CardResponse response = cardService.issueDebitCard(request);

        // JUnit 4 Assert calls
        Assert.assertNotNull("suiteContextId from @BeforeClass must not be null", suiteContextId);
        Assert.assertNotNull("Card response should not be null", response);
        Assert.assertEquals("Account ID mismatch", "acc-legacy-01", response.getAccountId());
        Assert.assertEquals("Initial status must be INACTIVE", CardStatus.INACTIVE, response.getCardStatus());
        Assert.assertEquals("Network must be VISA", CardNetwork.VISA, response.getCardNetwork());
        Assert.assertTrue("Masked card must start with 4", response.getMaskedCardNumber().startsWith("4"));
        Assert.assertNotNull("Token must be present", response.getCardToken());

        // Mockito 3 ArgumentCaptor & verify
        ArgumentCaptor<CardEntity> entityCaptor = ArgumentCaptor.forClass(CardEntity.class);
        verify(cardRepository, times(1)).save(entityCaptor.capture());
        CardEntity savedCard = entityCaptor.getValue();
        Assert.assertEquals("aes256_mock_cipher", savedCard.getCardNumberEncrypted());
    }

    @Test
    public void testActivateCard_Success() {
        String cvv = "456";
        CardEntity entity = new CardEntity(
                "crd-legacy-99",
                "tok-legacy-99",
                "cipher_data",
                "hash_data",
                "4111 **** **** 9999",
                "acc-legacy-01",
                "Legacy Test User",
                CardNetwork.VISA,
                CardSecurityUtils.hashCvv(cvv),
                "08/29",
                new BigDecimal("1000.00")
        );
        entity.setCardStatus(CardStatus.INACTIVE);

        when(cardRepository.findById("crd-legacy-99")).thenReturn(Optional.of(entity));
        when(cardRepository.save(any(CardEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CardActivationRequest actReq = new CardActivationRequest();
        actReq.setCardId("crd-legacy-99");
        actReq.setCvv(cvv);
        actReq.setPin("5555");

        CardResponse activated = cardService.activateDebitCard(actReq);

        Assert.assertNotNull(activated);
        Assert.assertEquals(CardStatus.ACTIVE, activated.getCardStatus());
        Assert.assertTrue(CardSecurityUtils.verifyPin("5555", entity.getPinHash()));
    }

    @Test
    public void testActivateCard_ThrowsBankingExceptionOnInvalidCvv() {
        CardEntity entity = new CardEntity(
                "crd-legacy-99",
                "tok-legacy-99",
                "cipher_data",
                "hash_data",
                "4111 **** **** 9999",
                "acc-legacy-01",
                "Legacy Test User",
                CardNetwork.VISA,
                CardSecurityUtils.hashCvv("123"),
                "08/29",
                new BigDecimal("1000.00")
        );
        entity.setCardStatus(CardStatus.INACTIVE);

        when(cardRepository.findById("crd-legacy-99")).thenReturn(Optional.of(entity));

        CardActivationRequest actReq = new CardActivationRequest();
        actReq.setCardId("crd-legacy-99");
        actReq.setCvv("999"); // Wrong CVV
        actReq.setPin("5555");

        // Use JUnit 4 ExpectedException @Rule
        thrown.expect(BankingException.class);
        thrown.expectMessage("Invalid CVV provided for activation");

        cardService.activateDebitCard(actReq);
    }
}
