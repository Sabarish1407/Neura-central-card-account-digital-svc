package com.nuera.banking.card.jpa;

import com.nuera.banking.card.model.enums.AccountStatus;
import com.nuera.banking.card.model.enums.AccountType;
import com.nuera.banking.card.service.entity.AccountEntity;
import com.nuera.banking.card.service.repository.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@DisplayName("JPA Tests: AccountRepository")
class AccountRepositoryJpaTest {

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("Save and query account by ID, number, and customer")
    void testSaveAndQueryAccount() {
        AccountEntity account = new AccountEntity(
                "acc-jpa-1",
                "1099887766",
                "cust-jpa-1",
                "Jane Doe",
                AccountType.CHECKING,
                BigDecimal.valueOf(500.00),
                "USD",
                AccountStatus.ACTIVE
        );

        accountRepository.save(account);

        Optional<AccountEntity> byNumber = accountRepository.findByAccountNumber("1099887766");
        assertTrue(byNumber.isPresent());
        assertEquals("Jane Doe", byNumber.get().getCustomerName());

        List<AccountEntity> byCustomer = accountRepository.findByCustomerId("cust-jpa-1");
        assertEquals(1, byCustomer.size());
        assertEquals(BigDecimal.valueOf(500.00), byCustomer.get(0).getBalance());
    }
}
