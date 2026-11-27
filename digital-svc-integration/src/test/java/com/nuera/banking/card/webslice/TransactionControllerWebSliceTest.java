package com.nuera.banking.card.webslice;

import tools.jackson.databind.ObjectMapper;
import com.nuera.banking.card.model.dto.TransactionAuthorizationRequest;
import com.nuera.banking.card.model.dto.TransactionAuthorizationResponse;
import com.nuera.banking.card.model.dto.TransactionResponse;
import com.nuera.banking.card.model.enums.TransactionChannel;
import com.nuera.banking.card.model.enums.TransactionStatus;
import com.nuera.banking.card.model.enums.TransactionType;
import com.nuera.banking.card.service.controller.TransactionController;
import com.nuera.banking.card.service.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;

@Tag("neuro-component")
@WebMvcTest(TransactionController.class)
@DisplayName("Web Slice Tests: TransactionController")
class TransactionControllerWebSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private TransactionService transactionService;

    @Test
    @DisplayName("POST /api/v1/transactions/authorize returns 200 OK with approval")
    void testAuthorizeTransactionEndpoint() throws Exception {
        TransactionAuthorizationResponse response = new TransactionAuthorizationResponse();
        response.setTransactionId("tx-123");
        response.setStatus(TransactionStatus.APPROVED);
        response.setAuthorizationCode("AUTH123456");
        response.setAmount(BigDecimal.valueOf(99.99));
        response.setCurrency("USD");

        when(transactionService.authorizeTransaction(any(TransactionAuthorizationRequest.class))).thenReturn(response);

        TransactionAuthorizationRequest request = new TransactionAuthorizationRequest();
        request.setCardNumber("tok-abc");
        request.setPin("1234");
        request.setAmount(BigDecimal.valueOf(99.99));
        request.setCurrency("USD");

        mockMvc.perform(post("/api/v1/transactions/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("tx-123"))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.authorizationCode").value("AUTH123456"));
    }

    @Test
    @DisplayName("GET /api/v1/transactions/card/{cardId} returns list of transactions")
    void testGetCardTransactionsEndpoint() throws Exception {
        TransactionResponse tx = new TransactionResponse();
        tx.setTransactionId("tx-001");
        tx.setCardId("card-101");
        tx.setAmount(BigDecimal.valueOf(50.00));
        tx.setTransactionType(TransactionType.PURCHASE);
        tx.setStatus(TransactionStatus.APPROVED);
        tx.setChannel(TransactionChannel.ECOMMERCE);
        tx.setTimestamp(Instant.now());

        when(transactionService.getTransactionsForCard("card-101")).thenReturn(Collections.singletonList(tx));

        mockMvc.perform(get("/api/v1/transactions/card/card-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].transactionId").value("tx-001"))
                .andExpect(jsonPath("$[0].amount").value(50.00));
    }
}
