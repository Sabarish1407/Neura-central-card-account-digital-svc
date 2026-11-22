package com.nuera.banking.card.unit;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Unit Tests: CardService")
class CardServiceUnitTest {

    @Mock
    private CardRepository cardRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private EncryptionService encryptionService;

    @InjectMocks
    private CardService cardService;

    private AccountEntity mockAccount;

    @BeforeEach
    void setUp() {
        mockAccount = new AccountEntity(
                "acc-12345",
                "1000123456",
                "cust-999",
                "Alice Bob",
                AccountType.CHECKING,
                BigDecimal.valueOf(1000.00),
                "USD",
                AccountStatus.ACTIVE
        );
    }

    @Test
    @DisplayName("Issue VISA debit card successfully with masked PAN and token")
    void testIssueVisaDebitCard() {
        when(accountRepository.findById("acc-12345")).thenReturn(Optional.of(mockAccount));
        when(encryptionService.encrypt(any())).thenReturn("encrypted_cipher_text");
        when(cardRepository.save(any(CardEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardIssuanceRequest request = new CardIssuanceRequest();
        request.setAccountId("acc-12345");
        request.setCardHolderName("Alice Bob");
        request.setDailyLimit(BigDecimal.valueOf(1500.00));

        CardResponse response = cardService.issueDebitCard(request);

        assertNotNull(response);
        assertEquals(CardStatus.INACTIVE, response.getCardStatus());
        assertEquals(CardNetwork.VISA, response.getCardNetwork());
        assertEquals("acc-12345", response.getAccountId());
        assertTrue(response.getMaskedCardNumber().startsWith("4"));
        assertTrue(response.getMaskedCardNumber().contains("*"));
        assertNotNull(response.getCardToken());

        verify(cardRepository, times(1)).save(any(CardEntity.class));
    }

    @Test
    @DisplayName("Activate debit card with valid CVV and set salted BCrypt PIN")
    void testActivateCardSuccessfully() {
        String cvv = "345";
        String cvvHash = CardSecurityUtils.hashCvv(cvv);

        CardEntity card = new CardEntity(
                "card-101",
                "tok-abc",
                "encrypted_pan",
                "hash_pan",
                "4111********1111",
                "acc-12345",
                "Alice Bob",
                CardNetwork.VISA,
                cvvHash,
                "12/28",
                BigDecimal.valueOf(1000)
        );
        card.setCardStatus(CardStatus.INACTIVE);

        when(cardRepository.findById("card-101")).thenReturn(Optional.of(card));
        when(cardRepository.save(any(CardEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardActivationRequest activationReq = new CardActivationRequest();
        activationReq.setCardId("card-101");
        activationReq.setCvv("345");
        activationReq.setPin("4321");

        CardResponse response = cardService.activateDebitCard(activationReq);

        assertNotNull(response);
        assertEquals(CardStatus.ACTIVE, response.getCardStatus());
        assertNotNull(card.getPinHash());
        assertTrue(CardSecurityUtils.verifyPin("4321", card.getPinHash()));
    }

    @Test
    @DisplayName("Activate debit card fails when CVV is incorrect")
    void testActivateCardFailsWithIncorrectCvv() {
        String cvvHash = CardSecurityUtils.hashCvv("345");

        CardEntity card = new CardEntity(
                "card-101",
                "tok-abc",
                "encrypted_pan",
                "hash_pan",
                "4111********1111",
                "acc-12345",
                "Alice Bob",
                CardNetwork.VISA,
                cvvHash,
                "12/28",
                BigDecimal.valueOf(1000)
        );

        when(cardRepository.findById("card-101")).thenReturn(Optional.of(card));

        CardActivationRequest activationReq = new CardActivationRequest();
        activationReq.setCardId("card-101");
        activationReq.setCvv("999"); // Wrong CVV
        activationReq.setPin("4321");

        assertThrows(BankingException.class, () -> cardService.activateDebitCard(activationReq));
    }

    @Test
    @DisplayName("Freeze and unfreeze card transitions status appropriately")
    void testFreezeAndUnfreezeCard() {
        CardEntity card = new CardEntity(
                "card-101",
                "tok-abc",
                "encrypted_pan",
                "hash_pan",
                "4111********1111",
                "acc-12345",
                "Alice Bob",
                CardNetwork.VISA,
                "cvvHash",
                "12/28",
                BigDecimal.valueOf(1000)
        );
        card.setCardStatus(CardStatus.ACTIVE);

        when(cardRepository.findById("card-101")).thenReturn(Optional.of(card));
        when(cardRepository.save(any(CardEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CardResponse frozen = cardService.freezeCard("card-101");
        assertEquals(CardStatus.FROZEN, frozen.getCardStatus());

        CardResponse unfrozen = cardService.unfreezeCard("card-101");
        assertEquals(CardStatus.ACTIVE, unfrozen.getCardStatus());
    }
}
