/*
 * HMDM-EVOLUTION F1.6: View model for command state (mapped from v_command_state).
 */

package com.hmdm.notification.rest.json;

import java.io.Serializable;
import java.util.List;

public class CommandStateView implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer messageId;
    private Long pendingId;
    private Integer deviceId;
    private String messageType;
    private String payload;
    private Long createdAt;
    private Long sentAt;
    private Long deliveredAckAt;
    private Long executedAckAt;
    private Long failedAt;
    private Long expiresAt;
    private Integer retryCount;
    private Integer maxRetries;
    private String failureCode;
    private String failureMessage;
    private Long deviceSequenceNum;
    private String correlationId;
    private Integer createdByUserId;
    private String createdBySource;
    private String state;

    // Optional fields populated in detail view
    private List<CommandAuditView> auditTrail;

    public Integer getMessageId() { return messageId; }
    public void setMessageId(Integer messageId) { this.messageId = messageId; }

    public Long getPendingId() { return pendingId; }
    public void setPendingId(Long pendingId) { this.pendingId = pendingId; }

    public Integer getDeviceId() { return deviceId; }
    public void setDeviceId(Integer deviceId) { this.deviceId = deviceId; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public Long getCreatedAt() { return createdAt; }
    public void setCreatedAt(Long createdAt) { this.createdAt = createdAt; }

    public Long getSentAt() { return sentAt; }
    public void setSentAt(Long sentAt) { this.sentAt = sentAt; }

    public Long getDeliveredAckAt() { return deliveredAckAt; }
    public void setDeliveredAckAt(Long deliveredAckAt) { this.deliveredAckAt = deliveredAckAt; }

    public Long getExecutedAckAt() { return executedAckAt; }
    public void setExecutedAckAt(Long executedAckAt) { this.executedAckAt = executedAckAt; }

    public Long getFailedAt() { return failedAt; }
    public void setFailedAt(Long failedAt) { this.failedAt = failedAt; }

    public Long getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Long expiresAt) { this.expiresAt = expiresAt; }

    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }

    public Integer getMaxRetries() { return maxRetries; }
    public void setMaxRetries(Integer maxRetries) { this.maxRetries = maxRetries; }

    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }

    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }

    public Long getDeviceSequenceNum() { return deviceSequenceNum; }
    public void setDeviceSequenceNum(Long deviceSequenceNum) { this.deviceSequenceNum = deviceSequenceNum; }

    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

    public Integer getCreatedByUserId() { return createdByUserId; }
    public void setCreatedByUserId(Integer createdByUserId) { this.createdByUserId = createdByUserId; }

    public String getCreatedBySource() { return createdBySource; }
    public void setCreatedBySource(String createdBySource) { this.createdBySource = createdBySource; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public List<CommandAuditView> getAuditTrail() { return auditTrail; }
    public void setAuditTrail(List<CommandAuditView> auditTrail) { this.auditTrail = auditTrail; }
}
