package com.nuera.banking.card.service.repository;

import com.nuera.banking.card.model.enums.TransactionStatus;
import com.nuera.banking.card.service.entity.TransactionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<TransactionEntity, String> {
    List<TransactionEntity> findByCardIdOrderByTimestampDesc(String cardId);
    List<TransactionEntity> findByAccountIdOrderByTimestampDesc(String accountId);
    List<TransactionEntity> findByCardIdAndStatusAndTimestampBetween(
            String cardId, TransactionStatus status, Instant start, Instant end
    );
}
