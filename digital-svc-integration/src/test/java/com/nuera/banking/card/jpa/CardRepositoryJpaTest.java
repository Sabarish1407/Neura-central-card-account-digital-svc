package com.nuera.banking.card.jpa;

import com.nuera.banking.card.model.enums.CardNetwork;
import com.nuera.banking.card.model.enums.CardStatus;
import com.nuera.banking.card.service.entity.CardEntity;
import com.nuera.banking.card.service.repository.CardRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("JPA Tests: CardRepository")
class CardRepositoryJpaTest {

    @Autowired
    private CardRepository cardRepository;

    @Test
    @DisplayName("Save and find card by token and card ID")
    void testSaveAndFindByToken() {
        CardEntity card = new CardEntity(
                "card-test-01",
                "tok-test-01",
                "cipher_abc123",
                "sha256_hash123",
                "4111********1111",
                "acc-test-01",
                "John Doe",
                CardNetwork.VISA,
                "cvv_hash",
                "12/28",
                BigDecimal.valueOf(1000.00)
        );
        card.setCardStatus(CardStatus.ACTIVE);

        cardRepository.save(card);

        Optional<CardEntity> byToken = cardRepository.findByCardToken("tok-test-01");
        assertTrue(byToken.isPresent());
        assertEquals("card-test-01", byToken.get().getCardId());
        assertEquals("John Doe", byToken.get().getCardHolderName());

        Optional<CardEntity> byHash = cardRepository.findByCardNumberHash("sha256_hash123");
        assertTrue(byHash.isPresent());
        assertEquals("tok-test-01", byHash.get().getCardToken());

        List<CardEntity> byAccount = cardRepository.findByAccountId("acc-test-01");
        assertEquals(1, byAccount.size());
    }
}
