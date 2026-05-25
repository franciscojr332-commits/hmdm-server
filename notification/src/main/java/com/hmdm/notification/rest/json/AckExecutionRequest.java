/*
 * HMDM-EVOLUTION F2: Execution ACK request payload from agent.
 */

package com.hmdm.notification.rest.json;

import java.io.Serializable;

public class AckExecutionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String STATUS_OK = "OK";
    public static final String STATUS_FAILED = "FAILED";

    private String deviceNumber;
    private Integer messageId;
    private Long executedAt;
    private String status;
    private String failureCode;
    private String failureMessage;
    private String resultPayload;

    public AckExecutionRequest() {}

    public String getDeviceNumber() { return deviceNumber; }
    public void setDeviceNumber(String deviceNumber) { this.deviceNumber = deviceNumber; }

    public Integer getMessageId() { return messageId; }
    public void setMessageId(Integer messageId) { this.messageId = messageId; }

    public Long getExecutedAt() { return executedAt; }
    public void setExecutedAt(Long executedAt) { this.executedAt = executedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFailureCode() { return failureCode; }
    public void setFailureCode(String failureCode) { this.failureCode = failureCode; }

    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }

    public String getResultPayload() { return resultPayload; }
    public void setResultPayload(String resultPayload) { this.resultPayload = resultPayload; }
}
