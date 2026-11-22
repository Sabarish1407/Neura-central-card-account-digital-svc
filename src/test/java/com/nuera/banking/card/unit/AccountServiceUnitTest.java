package com.nuera.banking.card.unit;

import com.nuera.banking.card.model.dto.AccountCreateRequest;
import com.nuera.banking.card.model.dto.AccountDepositRequest;
import com.nuera.banking.card.model.dto.AccountResponse;
import com.nuera.banking.card.model.enums.AccountStatus;
import com.nuera.banking.card.model.enums.AccountType;
import com.nuera.banking.card.service.entity.AccountEntity;
import com.nuera.banking.card.service.exception.ResourceNotFoundException;
import com.nuera.banking.card.service.repository.AccountRepository;
import com.nuera.banking.card.service.service.AccountService;
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
@DisplayName("Unit Tests: AccountService")
class AccountServiceUnitTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    @DisplayName("Create digital banking account successfully")
    void testCreateAccount() {
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(i -> i.getArgument(0));

        AccountCreateRequest request = new AccountCreateRequest();
        request.setCustomerId("cust-100");
        request.setCustomerName("Bob Builder");
        request.setAccountType(AccountType.CHECKING);
        request.setInitialDeposit(BigDecimal.valueOf(500.00));
        request.setCurrency("USD");

        AccountResponse response = accountService.createAccount(request);

        assertNotNull(response);
        assertEquals("cust-100", response.getCustomerId());
        assertEquals("Bob Builder", response.getCustomerName());
        assertEquals(BigDecimal.valueOf(500.00), response.getBalance());
        assertEquals(AccountStatus.ACTIVE, response.getStatus());

        verify(accountRepository, times(1)).save(any(AccountEntity.class));
    }

    @Test
    @DisplayName("Deposit funds into account successfully")
    void testDepositFunds() {
        AccountEntity account = new AccountEntity(
                "acc-200",
                "1000000002",
                "cust-200",
                "Charlie",
                AccountType.SAVINGS,
                BigDecimal.valueOf(100.00),
                "USD",
                AccountStatus.ACTIVE
        );

        when(accountRepository.findById("acc-200")).thenReturn(Optional.of(account));
        when(accountRepository.save(any(AccountEntity.class))).thenAnswer(i -> i.getArgument(0));

        AccountDepositRequest depositReq = new AccountDepositRequest();
        depositReq.setAmount(BigDecimal.valueOf(250.00));

        AccountResponse response = accountService.depositFunds("acc-200", depositReq);

        assertNotNull(response);
        assertEquals(BigDecimal.valueOf(350.00), response.getBalance());
        assertEquals(BigDecimal.valueOf(350.00), response.getAvailableBalance());
    }

    @Test
    @DisplayName("Get account by non-existent ID throws ResourceNotFoundException")
    void testGetNonExistentAccount() {
        when(accountRepository.findById("invalid")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> accountService.getAccountById("invalid"));
    }
}
