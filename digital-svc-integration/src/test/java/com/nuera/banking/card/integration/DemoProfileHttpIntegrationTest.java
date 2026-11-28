package com.nuera.banking.card.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Tag;

@Tag("neuro-integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("demo")
@AutoConfigureTestRestTemplate
@DisplayName("HTTP Integration Tests: Demo Profile and Swagger Disablement")
class DemoProfileHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    @DisplayName("Demo profile starts cleanly and Swagger UI answers 404")
    void testSwaggerDisabledInDemoProfile() {
        // Swagger UI endpoint should be disabled (404)
        ResponseEntity<String> swaggerResponse = restTemplate.getForEntity(
                getBaseUrl() + "/swagger-ui.html",
                String.class
        );
        assertEquals(HttpStatus.NOT_FOUND, swaggerResponse.getStatusCode());

        // OpenAPI JSON docs should also be disabled (404)
        ResponseEntity<String> apiDocsResponse = restTemplate.getForEntity(
                getBaseUrl() + "/v3/api-docs",
                String.class
        );
        assertEquals(HttpStatus.NOT_FOUND, apiDocsResponse.getStatusCode());
    }
}
