package com.nuera.banking.card.service.repository;

import com.nuera.banking.card.service.entity.CardEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CardRepository extends JpaRepository<CardEntity, String> {
    Optional<CardEntity> findByCardToken(String cardToken);
    Optional<CardEntity> findByCardNumberHash(String cardNumberHash);
    List<CardEntity> findByAccountId(String accountId);
}
