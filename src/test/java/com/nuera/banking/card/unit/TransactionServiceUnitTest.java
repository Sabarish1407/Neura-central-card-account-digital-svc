package com.nuera.banking.card.unit;

import com.nuera.banking.card.model.dto.TransactionAuthorizationRequest;
import com.nuera.banking.card.model.dto.TransactionAuthorizationResponse;
import com.nuera.banking.card.model.enums.*;
import com.nuera.banking.card.service.entity.AccountEntity;
import com.nuera.banking.card.service.entity.CardEntity;
import com.nuera.banking.card.service.entity.TransactionEntity;
import com.nuera.banking.card.service.repository.AccountRepository;
import com.nuera.banking.card.service.repository.CardRepository;
import com.nuera.banking.card.service.repository.TransactionRepository;
import com.nuera.banking.card.service.service.TransactionService;
import com.nuera.banking.card.service.util.CardSecurityUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests: TransactionService")
class TransactionServiceUnitTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private CardRepository cardRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private TransactionService transactionService;

    private CardEntity activeCard;
    private AccountEntity fundedAccount;

    @BeforeEach
    void setUp() {
        fundedAccount = new AccountEntity(
                "acc-001",
                "1000000001",
                "cust-1",
                "John Doe",
                AccountType.CHECKING,
                BigDecimal.valueOf(2000.00),
                "USD",
                AccountStatus.ACTIVE
        );

        activeCard = new CardEntity(
                "card-001",
                "tok-001",
                "encrypted_pan",
                CardSecurityUtils.hashCardNumber("4111222233334444"),
                "4111********4444",
                "acc-001",
                "John Doe",
                CardNetwork.VISA,
                CardSecurityUtils.hashCvv("123"),
                "12/28",
                BigDecimal.valueOf(1000.00) // Daily limit
        );
        activeCard.setCardStatus(CardStatus.ACTIVE);
        activeCard.setPinHash(CardSecurityUtils.hashPin("1234")); // Salted BCrypt PIN
    }

    @Test
    @DisplayName("Authorize transaction successfully with correct PIN and sufficient balance")
    void testAuthorizeTransactionSuccess() {
        when(cardRepository.findByCardToken("tok-001")).thenReturn(Optional.of(activeCard));
        when(accountRepository.findById("acc-001")).thenReturn(Optional.of(fundedAccount));
        when(transactionRepository.findByCardIdAndStatusAndTimestampBetween(eq("card-001"), eq(TransactionStatus.APPROVED), any(), any()))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(i -> i.getArgument(0));

        TransactionAuthorizationRequest request = new TransactionAuthorizationRequest();
        request.setCardNumber("tok-001");
        request.setPin("1234");
        request.setAmount(BigDecimal.valueOf(150.00));
        request.setCurrency("USD");
        request.setMerchantName("Amazon.com");

        TransactionAuthorizationResponse response = transactionService.authorizeTransaction(request);

        assertNotNull(response);
        assertEquals(TransactionStatus.APPROVED, response.getStatus());
        assertNotNull(response.getApprovalCode());
        assertEquals(BigDecimal.valueOf(1850.00), fundedAccount.getBalance());
        assertEquals(0, activeCard.getFailedPinAttempts());
    }

    @Test
    @DisplayName("Decline transaction when PIN is incorrect and verify attempt increment")
    void testDeclineOnWrongPin() {
        when(cardRepository.findByCardToken("tok-001")).thenReturn(Optional.of(activeCard));
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(i -> i.getArgument(0));

        TransactionAuthorizationRequest request = new TransactionAuthorizationRequest();
        request.setCardNumber("tok-001");
        request.setPin("9999"); // Wrong PIN
        request.setAmount(BigDecimal.valueOf(50.00));
        request.setCurrency("USD");

        TransactionAuthorizationResponse response = transactionService.authorizeTransaction(request);

        assertEquals(TransactionStatus.DECLINED, response.getStatus());
        assertEquals(DeclineReason.INVALID_PIN, response.getDeclineReason());
        assertEquals(1, activeCard.getFailedPinAttempts());
        assertEquals(BigDecimal.valueOf(2000.00), fundedAccount.getBalance()); // Unchanged
    }

    @Test
    @DisplayName("Decline and block card on 3 consecutive wrong PIN attempts")
    void testBlockCardOnThreeFailedAttempts() {
        activeCard.setFailedPinAttempts(2); // Already 2 failed attempts

        when(cardRepository.findByCardToken("tok-001")).thenReturn(Optional.of(activeCard));
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(i -> i.getArgument(0));

        TransactionAuthorizationRequest request = new TransactionAuthorizationRequest();
        request.setCardNumber("tok-001");
        request.setPin("0000"); // 3rd wrong attempt
        request.setAmount(BigDecimal.valueOf(50.00));
        request.setCurrency("USD");

        TransactionAuthorizationResponse response = transactionService.authorizeTransaction(request);

        assertEquals(TransactionStatus.DECLINED, response.getStatus());
        assertTrue(response.getDeclineReason() == DeclineReason.PIN_TRIES_EXCEEDED || response.getDeclineReason() == DeclineReason.PIN_ATTEMPTS_EXCEEDED);
        assertEquals(CardStatus.BLOCKED, activeCard.getCardStatus());
    }

    @Test
    @DisplayName("Decline transaction when exceeding daily spend limit")
    void testDeclineOnDailyLimitBreach() {
        when(cardRepository.findByCardToken("tok-001")).thenReturn(Optional.of(activeCard));
        // Already spent 900 today, limit is 1000
        TransactionEntity existingTx = new TransactionEntity();
        existingTx.setAmount(BigDecimal.valueOf(900.00));

        when(transactionRepository.findByCardIdAndStatusAndTimestampBetween(eq("card-001"), eq(TransactionStatus.APPROVED), any(), any()))
                .thenReturn(Collections.singletonList(existingTx));
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(i -> i.getArgument(0));

        TransactionAuthorizationRequest request = new TransactionAuthorizationRequest();
        request.setCardNumber("tok-001");
        request.setPin("1234");
        request.setAmount(BigDecimal.valueOf(200.00)); // 900 + 200 = 1100 > 1000 limit
        request.setCurrency("USD");

        TransactionAuthorizationResponse response = transactionService.authorizeTransaction(request);

        assertEquals(TransactionStatus.DECLINED, response.getStatus());
        assertTrue(response.getDeclineReason() == DeclineReason.EXCEEDS_DAILY_LIMIT || response.getDeclineReason() == DeclineReason.DAILY_LIMIT_EXCEEDED);
        assertEquals(BigDecimal.valueOf(2000.00), fundedAccount.getBalance());
    }

    @Test
    @DisplayName("Decline transaction when account balance is insufficient")
    void testDeclineOnInsufficientFunds() {
        fundedAccount.setBalance(BigDecimal.valueOf(50.00));
        fundedAccount.setAvailableBalance(BigDecimal.valueOf(50.00));

        when(cardRepository.findByCardToken("tok-001")).thenReturn(Optional.of(activeCard));
        when(accountRepository.findById("acc-001")).thenReturn(Optional.of(fundedAccount));
        when(transactionRepository.findByCardIdAndStatusAndTimestampBetween(eq("card-001"), eq(TransactionStatus.APPROVED), any(), any()))
                .thenReturn(Collections.emptyList());
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(i -> i.getArgument(0));

        TransactionAuthorizationRequest request = new TransactionAuthorizationRequest();
        request.setCardNumber("tok-001");
        request.setPin("1234");
        request.setAmount(BigDecimal.valueOf(100.00)); // Need 100, have 50
        request.setCurrency("USD");

        TransactionAuthorizationResponse response = transactionService.authorizeTransaction(request);

        assertEquals(TransactionStatus.DECLINED, response.getStatus());
        assertEquals(DeclineReason.INSUFFICIENT_FUNDS, response.getDeclineReason());
    }

    @Test
    @DisplayName("Decline transaction when card is frozen")
    void testDeclineWhenCardFrozen() {
        activeCard.setCardStatus(CardStatus.FROZEN);

        when(cardRepository.findByCardToken("tok-001")).thenReturn(Optional.of(activeCard));
        when(transactionRepository.save(any(TransactionEntity.class))).thenAnswer(i -> i.getArgument(0));

        TransactionAuthorizationRequest request = new TransactionAuthorizationRequest();
        request.setCardNumber("tok-001");
        request.setPin("1234");
        request.setAmount(BigDecimal.valueOf(25.00));
        request.setCurrency("USD");

        TransactionAuthorizationResponse response = transactionService.authorizeTransaction(request);

        assertEquals(TransactionStatus.DECLINED, response.getStatus());
        assertEquals(DeclineReason.CARD_FROZEN, response.getDeclineReason());
    }
}
