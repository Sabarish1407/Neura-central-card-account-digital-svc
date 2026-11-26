package com.nuera.banking.card.webslice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuera.banking.card.model.dto.CardActivationRequest;
import com.nuera.banking.card.model.dto.CardIssuanceRequest;
import com.nuera.banking.card.model.dto.CardResponse;
import com.nuera.banking.card.model.enums.CardNetwork;
import com.nuera.banking.card.model.enums.CardStatus;
import com.nuera.banking.card.service.controller.CardController;
import com.nuera.banking.card.service.service.CardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CardController.class)
@DisplayName("Web Slice Tests: CardController")
class CardControllerWebSliceTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CardService cardService;

    @Test
    @DisplayName("POST /api/v1/cards/issue returns 201 Created")
    void testIssueCardEndpoint() throws Exception {
        CardResponse response = new CardResponse();
        response.setCardId("card-123");
        response.setAccountId("acc-123");
        response.setMaskedCardNumber("4111********1234");
        response.setCardStatus(CardStatus.INACTIVE);
        response.setCardNetwork(CardNetwork.VISA);

        when(cardService.issueDebitCard(any(CardIssuanceRequest.class))).thenReturn(response);

        CardIssuanceRequest request = new CardIssuanceRequest();
        request.setAccountId("acc-123");
        request.setCardHolderName("John Doe");

        mockMvc.perform(post("/api/v1/cards/issue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.cardId").value("card-123"))
                .andExpect(jsonPath("$.cardStatus").value("INACTIVE"))
                .andExpect(jsonPath("$.cardNetwork").value("VISA"));
    }

    @Test
    @DisplayName("POST /api/v1/cards/{cardId}/activate returns 200 OK")
    void testActivateCardEndpoint() throws Exception {
        CardResponse response = new CardResponse();
        response.setCardId("card-123");
        response.setCardStatus(CardStatus.ACTIVE);

        when(cardService.activateDebitCard(any(CardActivationRequest.class))).thenReturn(response);

        CardActivationRequest request = new CardActivationRequest();
        request.setCvv("123");
        request.setPin("1234");

        mockMvc.perform(post("/api/v1/cards/card-123/activate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value("card-123"))
                .andExpect(jsonPath("$.cardStatus").value("ACTIVE"));
    }

    @Test
    @DisplayName("POST /api/v1/cards/{cardId}/freeze returns 200 OK")
    void testFreezeCardEndpoint() throws Exception {
        CardResponse response = new CardResponse();
        response.setCardId("card-123");
        response.setCardStatus(CardStatus.FROZEN);

        when(cardService.freezeCard("card-123")).thenReturn(response);

        mockMvc.perform(post("/api/v1/cards/card-123/freeze"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value("card-123"))
                .andExpect(jsonPath("$.cardStatus").value("FROZEN"));
    }

    @Test
    @DisplayName("GET /api/v1/cards/{cardId} returns 200 OK")
    void testGetCardByIdEndpoint() throws Exception {
        CardResponse response = new CardResponse();
        response.setCardId("card-123");
        response.setMaskedCardNumber("4111********1234");

        when(cardService.getCardById("card-123")).thenReturn(response);

        mockMvc.perform(get("/api/v1/cards/card-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cardId").value("card-123"))
                .andExpect(jsonPath("$.maskedCardNumber").value("4111********1234"));
    }
}
