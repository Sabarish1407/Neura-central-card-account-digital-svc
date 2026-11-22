package com.nuera.banking.card.model.dto;

import java.math.BigDecimal;

public class RiskAssessmentResponse {

    private boolean approved;
    private int riskScore;
    private String riskLevel;
    private String reasonCode;

    public RiskAssessmentResponse() {
    }

    public RiskAssessmentResponse(boolean approved, int riskScore, String riskLevel, String reasonCode) {
        this.approved = approved;
        this.riskScore = riskScore;
        this.riskLevel = riskLevel;
        this.reasonCode = reasonCode;
    }

    public boolean isApproved() {
        return approved;
    }

    public void setApproved(boolean approved) {
        this.approved = approved;
    }

    public int getRiskScore() {
        return riskScore;
    }

    public void setRiskScore(int riskScore) {
        this.riskScore = riskScore;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }
}
