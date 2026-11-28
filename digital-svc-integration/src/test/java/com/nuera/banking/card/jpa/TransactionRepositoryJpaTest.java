package com.nuera.banking.card.jpa;

import com.nuera.banking.card.model.enums.TransactionChannel;
import com.nuera.banking.card.model.enums.TransactionStatus;
import com.nuera.banking.card.model.enums.TransactionType;
import com.nuera.banking.card.service.entity.TransactionEntity;
import com.nuera.banking.card.service.repository.TransactionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Tag;

@Tag("neuro-integration")
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("JPA Tests: TransactionRepository")
class TransactionRepositoryJpaTest {

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @DisplayName("Save transaction and query by card, account, and timestamp window")
    void testSaveAndQueryTransactions() {
        TransactionEntity tx1 = new TransactionEntity();
        tx1.setTransactionId("tx-jpa-01");
        tx1.setCardId("card-jpa-01");
        tx1.setAccountId("acc-jpa-01");
        tx1.setReferenceNumber("ref-01");
        tx1.setAmount(BigDecimal.valueOf(100.00));
        tx1.setCurrency("USD");
        tx1.setTransactionType(TransactionType.PURCHASE);
        tx1.setStatus(TransactionStatus.APPROVED);
        tx1.setChannel(TransactionChannel.ECOMMERCE);
        tx1.setMerchantName("Amazon");
        tx1.setTimestamp(Instant.now().minus(2, ChronoUnit.HOURS));

        TransactionEntity tx2 = new TransactionEntity();
        tx2.setTransactionId("tx-jpa-02");
        tx2.setCardId("card-jpa-01");
        tx2.setAccountId("acc-jpa-01");
        tx2.setReferenceNumber("ref-02");
        tx2.setAmount(BigDecimal.valueOf(50.00));
        tx2.setCurrency("USD");
        tx2.setTransactionType(TransactionType.PURCHASE);
        tx2.setStatus(TransactionStatus.APPROVED);
        tx2.setChannel(TransactionChannel.POS);
        tx2.setMerchantName("Starbucks");
        tx2.setTimestamp(Instant.now().minus(1, ChronoUnit.HOURS));

        transactionRepository.save(tx1);
        transactionRepository.save(tx2);

        List<TransactionEntity> byCard = transactionRepository.findByCardIdOrderByTimestampDesc("card-jpa-01");
        assertEquals(2, byCard.size());

        List<TransactionEntity> byAccount = transactionRepository.findByAccountIdOrderByTimestampDesc("acc-jpa-01");
        assertEquals(2, byAccount.size());

        Instant start = Instant.now().minus(24, ChronoUnit.HOURS);
        Instant end = Instant.now().plus(1, ChronoUnit.HOURS);
        List<TransactionEntity> window = transactionRepository.findByCardIdAndStatusAndTimestampBetween("card-jpa-01", TransactionStatus.APPROVED, start, end);
        assertEquals(2, window.size());
    }
}
