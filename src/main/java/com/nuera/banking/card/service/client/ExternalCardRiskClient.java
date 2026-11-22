package com.nuera.banking.card.service.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuera.banking.card.model.dto.RiskAssessmentResponse;
import jakarta.annotation.PreDestroy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * External risk assessment client communicating with remote card fraud screening network.
 * Implemented using Apache HttpClient 4 (org.apache.httpcomponents:httpclient:4.5.14)
 * as part of the Central Neuro legacy dependency migration benchmark.
 */
@Service
public class ExternalCardRiskClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ExternalCardRiskClient.class);

    private final String riskServiceBaseUrl;
    private final boolean riskServiceEnabled;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    @org.springframework.beans.factory.annotation.Autowired
    public ExternalCardRiskClient(
            @Value("${banking.risk-service.base-url:http://localhost:8089/api/v1/fraud}") String riskServiceBaseUrl,
            @Value("${banking.risk-service.enabled:false}") boolean riskServiceEnabled,
            @Value("${banking.risk-service.connect-timeout-ms:3000}") int connectTimeoutMs,
            @Value("${banking.risk-service.socket-timeout-ms:3000}") int socketTimeoutMs,
            @Value("${banking.risk-service.connection-request-timeout-ms:2000}") int connRequestTimeoutMs,
            ObjectMapper objectMapper) {

        this.riskServiceBaseUrl = riskServiceBaseUrl;
        this.riskServiceEnabled = riskServiceEnabled;
        this.objectMapper = objectMapper;

        // Legacy Apache HttpClient 4 Connection & Timeout Configuration
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeoutMs)
                .setSocketTimeout(socketTimeoutMs)
                .setConnectionRequestTimeout(connRequestTimeoutMs)
                .build();

        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(50);
        cm.setDefaultMaxPerRoute(20);

        this.httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    /**
     * Constructor for custom CloseableHttpClient injection (e.g. testing with mocks).
     */
    public ExternalCardRiskClient(String riskServiceBaseUrl, boolean riskServiceEnabled,
                                  CloseableHttpClient httpClient, ObjectMapper objectMapper) {
        this.riskServiceBaseUrl = riskServiceBaseUrl;
        this.riskServiceEnabled = riskServiceEnabled;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Assesses transaction risk by issuing an HTTP POST to the external fraud screening service.
     */
    public RiskAssessmentResponse assessTransactionRisk(String cardId, BigDecimal amount, String merchantName) {
        if (cardId == null || cardId.trim().isEmpty()) {
            throw new IllegalArgumentException("Card ID must not be null or empty");
        }

        if (!riskServiceEnabled) {
            log.debug("External risk evaluation disabled; defaulting to approved.");
            return new RiskAssessmentResponse(true, 10, "LOW", "DEFAULT_FALLBACK");
        }

        String endpoint = riskServiceBaseUrl + "/assess";
        HttpPost postRequest = new HttpPost(endpoint);

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("cardId", cardId);
            payload.put("amount", amount);
            payload.put("merchantName", merchantName);

            String jsonPayload = objectMapper.writeValueAsString(payload);
            StringEntity entity = new StringEntity(jsonPayload, ContentType.APPLICATION_JSON);
            postRequest.setEntity(entity);
            postRequest.setHeader("Accept", "application/json");

            try (CloseableHttpResponse response = httpClient.execute(postRequest)) {
                int statusCode = response.getStatusLine().getStatusCode();
                HttpEntity responseEntity = response.getEntity();
                String responseBody = responseEntity != null ? EntityUtils.toString(responseEntity, StandardCharsets.UTF_8) : "";

                if (statusCode == HttpStatus.SC_OK) {
                    return objectMapper.readValue(responseBody, RiskAssessmentResponse.class);
                } else {
                    log.warn("Risk screening returned non-200 status: {}", statusCode);
                    return new RiskAssessmentResponse(true, 50, "MEDIUM", "SERVICE_NON_200");
                }
            }
        } catch (IOException e) {
            log.error("Failed to query external risk service at {}: {}", endpoint, e.getMessage());
            // Graceful degradation: in case of network issue, fallback to low-risk pass-through
            return new RiskAssessmentResponse(true, 25, "LOW", "NETWORK_FALLBACK");
        }
    }

    /**
     * Health check endpoint to verify remote fraud screening service availability using HTTP GET.
     */
    public boolean checkServiceHealth() {
        HttpGet getRequest = new HttpGet(riskServiceBaseUrl + "/health");
        try (CloseableHttpResponse response = httpClient.execute(getRequest)) {
            int statusCode = response.getStatusLine().getStatusCode();
            EntityUtils.consumeQuietly(response.getEntity());
            return statusCode == HttpStatus.SC_OK;
        } catch (IOException e) {
            log.warn("Risk service health ping failed: {}", e.getMessage());
            return false;
        }
    }

    public CloseableHttpClient getHttpClient() {
        return httpClient;
    }

    @Override
    @PreDestroy
    public void close() {
        try {
            if (httpClient != null) {
                httpClient.close();
                log.info("Closed Apache HttpClient 4 instance.");
            }
        } catch (IOException e) {
            log.warn("Error closing Apache HttpClient: {}", e.getMessage());
        }
    }
}
