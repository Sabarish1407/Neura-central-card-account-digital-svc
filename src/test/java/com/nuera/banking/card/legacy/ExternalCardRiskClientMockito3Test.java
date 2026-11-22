package com.nuera.banking.card.legacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuera.banking.card.model.dto.RiskAssessmentResponse;
import com.nuera.banking.card.service.client.ExternalCardRiskClient;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Legacy JUnit 4 + Mockito 3 unit test covering ExternalCardRiskClient.
 * Tests Apache HttpClient 4 (org.apache.httpcomponents:httpclient:4.5.14).
 * Serves as a target migration fixture for Central Neuro's OpenRewrite upgrade rules.
 */
@RunWith(MockitoJUnitRunner.class)
public class ExternalCardRiskClientMockito3Test {

    private static String testRunIdentifier;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Mock
    private CloseableHttpClient httpClient;

    @Mock
    private CloseableHttpResponse httpResponse;

    @Mock
    private StatusLine statusLine;

    @Mock
    private HttpEntity httpEntity;

    private ObjectMapper objectMapper;
    private ExternalCardRiskClient riskClient;

    @BeforeClass
    public static void beforeClass() {
        testRunIdentifier = "JUNIT4-MOCKITO3-SUITE-" + System.currentTimeMillis();
    }

    @AfterClass
    public static void afterClass() {
        testRunIdentifier = null;
    }

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
        // Construct client with mocked Apache HttpClient 4
        riskClient = new ExternalCardRiskClient(
                "http://mock-risk-gateway.internal/api/v1/fraud",
                true,
                httpClient,
                objectMapper
        );
    }

    @After
    public void tearDown() {
        riskClient.close();
    }

    @Test
    public void testAssessTransactionRisk_NullCardId_ThrowsIllegalArgumentException() {
        thrown.expect(IllegalArgumentException.class);
        thrown.expectMessage("Card ID must not be null or empty");

        riskClient.assessTransactionRisk(null, new BigDecimal("50.00"), "TestStore");
    }

    @Test
    public void testAssessTransactionRisk_ApprovedResponse() throws Exception {
        Assert.assertNotNull("BeforeClass testRunIdentifier must be populated", testRunIdentifier);

        // Arrange
        RiskAssessmentResponse mockResult = new RiskAssessmentResponse(true, 15, "LOW", "CLEAN");
        String jsonBody = objectMapper.writeValueAsString(mockResult);

        when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(jsonBody.getBytes(StandardCharsets.UTF_8)));
        when(httpResponse.getEntity()).thenReturn(httpEntity);
        when(httpClient.execute(any(HttpPost.class))).thenReturn(httpResponse);

        // Act
        RiskAssessmentResponse result = riskClient.assessTransactionRisk("crd-123", new BigDecimal("150.00"), "Amazon");

        // Assert using JUnit 4 Assert
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertTrue("Transaction should be approved", result.isApproved());
        Assert.assertEquals("Risk score should match", 15, result.getRiskScore());
        Assert.assertEquals("LOW", result.getRiskLevel());

        // Verify using Mockito ArgumentCaptor
        ArgumentCaptor<HttpPost> postCaptor = ArgumentCaptor.forClass(HttpPost.class);
        verify(httpClient, times(1)).execute(postCaptor.capture());

        HttpPost capturedPost = postCaptor.getValue();
        Assert.assertEquals("http://mock-risk-gateway.internal/api/v1/fraud/assess", capturedPost.getURI().toString());
        Assert.assertEquals("application/json", capturedPost.getFirstHeader("Accept").getValue());
    }

    @Test
    public void testAssessTransactionRisk_DeclinedResponse() throws Exception {
        // Arrange
        RiskAssessmentResponse mockResult = new RiskAssessmentResponse(false, 92, "HIGH", "SUSPECTED_FRAUD");
        String jsonBody = objectMapper.writeValueAsString(mockResult);

        when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpEntity.getContent()).thenReturn(new ByteArrayInputStream(jsonBody.getBytes(StandardCharsets.UTF_8)));
        when(httpResponse.getEntity()).thenReturn(httpEntity);
        when(httpClient.execute(any(HttpPost.class))).thenReturn(httpResponse);

        // Act
        RiskAssessmentResponse result = riskClient.assessTransactionRisk("crd-999", new BigDecimal("9999.00"), "SuspiciousStore");

        // Assert
        Assert.assertNotNull(result);
        Assert.assertFalse("Transaction should be declined", result.isApproved());
        Assert.assertEquals(92, result.getRiskScore());
        Assert.assertEquals("HIGH", result.getRiskLevel());
        Assert.assertEquals("SUSPECTED_FRAUD", result.getReasonCode());
    }

    @Test
    public void testCheckServiceHealth_Success() throws Exception {
        when(statusLine.getStatusCode()).thenReturn(HttpStatus.SC_OK);
        when(httpResponse.getStatusLine()).thenReturn(statusLine);
        when(httpClient.execute(any(HttpGet.class))).thenReturn(httpResponse);

        boolean isHealthy = riskClient.checkServiceHealth();

        Assert.assertTrue("Health check should return true on 200 OK", isHealthy);

        ArgumentCaptor<HttpGet> getCaptor = ArgumentCaptor.forClass(HttpGet.class);
        verify(httpClient).execute(getCaptor.capture());
        Assert.assertTrue(getCaptor.getValue().getURI().toString().endsWith("/health"));
    }

    @Test
    public void testCheckServiceHealth_NetworkError() throws Exception {
        when(httpClient.execute(any(HttpGet.class))).thenThrow(new IOException("Connection refused"));

        boolean isHealthy = riskClient.checkServiceHealth();

        Assert.assertFalse("Health check should return false on network failure", isHealthy);
    }
}
