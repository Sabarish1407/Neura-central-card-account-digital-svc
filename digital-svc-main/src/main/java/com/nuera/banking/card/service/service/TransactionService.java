package com.nuera.banking.card.service.service;

import com.nuera.banking.card.model.dto.TransactionAuthorizationRequest;
import com.nuera.banking.card.model.dto.TransactionAuthorizationResponse;
import com.nuera.banking.card.model.dto.TransactionResponse;
import com.nuera.banking.card.model.enums.*;
import com.nuera.banking.card.service.entity.AccountEntity;
import com.nuera.banking.card.service.entity.CardEntity;
import com.nuera.banking.card.service.entity.TransactionEntity;
import com.nuera.banking.card.service.exception.ResourceNotFoundException;
import com.nuera.banking.card.service.repository.AccountRepository;
import com.nuera.banking.card.service.repository.CardRepository;
import com.nuera.banking.card.service.repository.TransactionRepository;
import com.nuera.banking.card.service.util.CardSecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final CardRepository cardRepository;
    private final AccountRepository accountRepository;

    public TransactionService(TransactionRepository transactionRepository,
                              CardRepository cardRepository,
                              AccountRepository accountRepository) {
        this.transactionRepository = transactionRepository;
        this.cardRepository = cardRepository;
        this.accountRepository = accountRepository;
    }

    /**
     * Authorises debit card transaction with salted BCrypt PIN check, daily limit, and balance verification.
     */
    @Transactional
    public TransactionAuthorizationResponse authorizeTransaction(TransactionAuthorizationRequest request) {
        String cardIdentifier = request.getCardNumber() != null ? request.getCardNumber().trim() : "";

        // Look up by token, ID, or SHA-256 hash of card number
        Optional<CardEntity> cardOpt = cardRepository.findByCardToken(cardIdentifier);
        if (cardOpt.isEmpty()) {
            cardOpt = cardRepository.findById(cardIdentifier);
        }
        if (cardOpt.isEmpty()) {
            String hash = CardSecurityUtils.hashCardNumber(cardIdentifier);
            cardOpt = cardRepository.findByCardNumberHash(hash);
        }

        if (cardOpt.isEmpty()) {
            TransactionEntity txn = recordDeclinedTransaction(null, null, request, DeclineReason.INVALID_CARD_DETAILS);
            return TransactionAuthorizationResponse.declined(DeclineReason.INVALID_CARD_DETAILS, "Card could not be found.", txn.getTransactionId());
        }

        CardEntity card = cardOpt.get();

        // 1. Verify Card Lifecycle Status
        if (card.getCardStatus() == CardStatus.INACTIVE) {
            TransactionEntity txn = recordDeclinedTransaction(card.getCardId(), card.getAccountId(), request, DeclineReason.CARD_INACTIVE);
            return TransactionAuthorizationResponse.declined(DeclineReason.CARD_INACTIVE, "Card is not activated yet.", txn.getTransactionId());
        }
        if (card.getCardStatus() == CardStatus.FROZEN) {
            TransactionEntity txn = recordDeclinedTransaction(card.getCardId(), card.getAccountId(), request, DeclineReason.CARD_FROZEN);
            return TransactionAuthorizationResponse.declined(DeclineReason.CARD_FROZEN, "Card is temporarily frozen.", txn.getTransactionId());
        }
        if (card.getCardStatus() == CardStatus.BLOCKED || card.getCardStatus() == CardStatus.LOST_OR_STOLEN) {
            TransactionEntity txn = recordDeclinedTransaction(card.getCardId(), card.getAccountId(), request, DeclineReason.CARD_BLOCKED);
            return TransactionAuthorizationResponse.declined(DeclineReason.CARD_BLOCKED, "Card is blocked or reported lost/stolen.", txn.getTransactionId());
        }

        // 2. Salted BCrypt PIN Verification
        if (!CardSecurityUtils.verifyPin(request.getPin(), card.getPinHash())) {
            card.setFailedPinAttempts(card.getFailedPinAttempts() + 1);
            DeclineReason reason = DeclineReason.INVALID_PIN;
            if (card.getFailedPinAttempts() >= 3) {
                card.setCardStatus(CardStatus.BLOCKED);
                reason = DeclineReason.PIN_TRIES_EXCEEDED;
            }
            card.setUpdatedAt(Instant.now());
            cardRepository.save(card);

            TransactionEntity txn = recordDeclinedTransaction(card.getCardId(), card.getAccountId(), request, reason);
            String msg = (reason == DeclineReason.PIN_TRIES_EXCEEDED || reason == DeclineReason.PIN_ATTEMPTS_EXCEEDED)
                    ? "Maximum PIN attempts exceeded. Card is now blocked."
                    : "Invalid PIN entered. Attempts remaining: " + (3 - card.getFailedPinAttempts());
            TransactionAuthorizationResponse resp = TransactionAuthorizationResponse.declined(reason, msg, txn.getTransactionId());
            resp.setAmount(request.getAmount());
            resp.setCurrency(request.getCurrency());
            return resp;
        } else {
            // Reset counter on successful PIN entry
            if (card.getFailedPinAttempts() > 0) {
                card.setFailedPinAttempts(0);
                card.setUpdatedAt(Instant.now());
                cardRepository.save(card);
            }
        }

        // 3. Daily Spending Limit Check
        Instant startOfDay = Instant.now().truncatedTo(ChronoUnit.DAYS);
        Instant endOfDay = startOfDay.plus(1, ChronoUnit.DAYS);
        List<TransactionEntity> todayApprovedTxns = transactionRepository.findByCardIdAndStatusAndTimestampBetween(
                card.getCardId(), TransactionStatus.APPROVED, startOfDay, endOfDay
        );

        BigDecimal currentDayTotal = todayApprovedTxns.stream()
                .map(TransactionEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal newTotal = currentDayTotal.add(request.getAmount());
        if (newTotal.compareTo(card.getDailyLimit()) > 0) {
            TransactionEntity txn = recordDeclinedTransaction(card.getCardId(), card.getAccountId(), request, DeclineReason.EXCEEDS_DAILY_LIMIT);
            TransactionAuthorizationResponse resp = TransactionAuthorizationResponse.declined(DeclineReason.EXCEEDS_DAILY_LIMIT, "Transaction exceeds card daily spending limit.", txn.getTransactionId());
            resp.setAmount(request.getAmount());
            resp.setCurrency(request.getCurrency());
            return resp;
        }
        BigDecimal remainingLimit = card.getDailyLimit().subtract(newTotal);

        // 4. Linked Account Balance Check
        AccountEntity account = accountRepository.findById(card.getAccountId()).orElse(null);
        if (account == null || account.getStatus() != AccountStatus.ACTIVE) {
            TransactionEntity txn = recordDeclinedTransaction(card.getCardId(), card.getAccountId(), request, DeclineReason.ACCOUNT_INACTIVE);
            return TransactionAuthorizationResponse.declined(DeclineReason.ACCOUNT_INACTIVE, "Linked bank account is not active.", txn.getTransactionId());
        }

        if (account.getAvailableBalance().compareTo(request.getAmount()) < 0) {
            TransactionEntity txn = recordDeclinedTransaction(card.getCardId(), card.getAccountId(), request, DeclineReason.INSUFFICIENT_FUNDS);
            TransactionAuthorizationResponse resp = TransactionAuthorizationResponse.declined(DeclineReason.INSUFFICIENT_FUNDS, "Insufficient available account balance.", txn.getTransactionId());
            resp.setAmount(request.getAmount());
            resp.setCurrency(request.getCurrency());
            return resp;
        }

        // 5. Approval & Deduct Balance
        account.setBalance(account.getBalance().subtract(request.getAmount()));
        account.setAvailableBalance(account.getAvailableBalance().subtract(request.getAmount()));
        account.setUpdatedAt(Instant.now());
        accountRepository.save(account);

        String authCode = CardSecurityUtils.generateAuthorizationCode();
        String txnId = "txn-" + UUID.randomUUID().toString().substring(0, 8);

        TransactionEntity txn = new TransactionEntity();
        txn.setTransactionId(txnId);
        txn.setCardId(card.getCardId());
        txn.setAccountId(card.getAccountId());
        txn.setReferenceNumber(CardSecurityUtils.generateReferenceNumber());
        txn.setAmount(request.getAmount());
        txn.setCurrency(request.getCurrency() != null ? request.getCurrency() : card.getCurrency());
        txn.setTransactionType(request.getTransactionType() != null ? request.getTransactionType() : TransactionType.PURCHASE);
        txn.setChannel(request.getChannel() != null ? request.getChannel() : TransactionChannel.POS_TERMINAL);
        txn.setStatus(TransactionStatus.APPROVED);
        txn.setMerchantName(request.getMerchantName());
        txn.setAuthorizationCode(authCode);
        txn.setDeclineReason(DeclineReason.NONE);
        txn.setTimestamp(Instant.now());

        transactionRepository.save(txn);

        TransactionAuthorizationResponse resp = TransactionAuthorizationResponse.approved(authCode, txnId, remainingLimit);
        resp.setAmount(request.getAmount());
        resp.setCurrency(txn.getCurrency());
        return resp;
    }

    private TransactionEntity recordDeclinedTransaction(String cardId, String accountId,
                                                        TransactionAuthorizationRequest request,
                                                        DeclineReason reason) {
        String txnId = "txn-" + UUID.randomUUID().toString().substring(0, 8);
        TransactionEntity txn = new TransactionEntity();
        txn.setTransactionId(txnId);
        txn.setCardId(cardId != null ? cardId : "UNKNOWN");
        txn.setAccountId(accountId != null ? accountId : "UNKNOWN");
        txn.setReferenceNumber(CardSecurityUtils.generateReferenceNumber());
        txn.setAmount(request.getAmount() != null ? request.getAmount() : BigDecimal.ZERO);
        txn.setCurrency(request.getCurrency() != null ? request.getCurrency() : "USD");
        txn.setTransactionType(request.getTransactionType() != null ? request.getTransactionType() : TransactionType.PURCHASE);
        txn.setChannel(request.getChannel() != null ? request.getChannel() : TransactionChannel.POS_TERMINAL);
        txn.setStatus(TransactionStatus.DECLINED);
        txn.setMerchantName(request.getMerchantName());
        txn.setAuthorizationCode(null);
        txn.setDeclineReason(reason);
        txn.setTimestamp(Instant.now());
        return transactionRepository.save(txn);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsForCard(String cardId) {
        return transactionRepository.findByCardIdOrderByTimestampDesc(cardId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByCardId(String cardId) {
        return getTransactionsForCard(cardId);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsForAccount(String accountId) {
        return transactionRepository.findByAccountIdOrderByTimestampDesc(accountId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> getTransactionsByAccountId(String accountId) {
        return getTransactionsForAccount(accountId);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getTransactionById(String transactionId) {
        TransactionEntity entity = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new ResourceNotFoundException("Transaction not found with ID: " + transactionId));
        return mapToResponse(entity);
    }

    private TransactionResponse mapToResponse(TransactionEntity entity) {
        TransactionResponse res = new TransactionResponse();
        res.setTransactionId(entity.getTransactionId());
        res.setCardId(entity.getCardId());
        res.setAccountId(entity.getAccountId());
        res.setReferenceNumber(entity.getReferenceNumber());
        res.setAmount(entity.getAmount());
        res.setCurrency(entity.getCurrency());
        res.setTransactionType(entity.getTransactionType());
        res.setChannel(entity.getChannel());
        res.setStatus(entity.getStatus());
        res.setMerchantName(entity.getMerchantName());
        res.setAuthorizationCode(entity.getAuthorizationCode());
        res.setDeclineReason(entity.getDeclineReason());
        res.setTimestamp(entity.getTimestamp());

        cardRepository.findById(entity.getCardId()).ifPresent(c -> res.setMaskedCardNumber(c.getCardNumberMasked()));
        return res;
    }
}
