package com.hmdm.plugins.terminal.persistence.domain;

public class TerminalCommand {
    private Long id;
    private Integer sessionId;
    private Integer deviceId;
    private String command;
    private String messageType;
    private Integer messageId;
    private Long sentAt;
    private Long completedAt;
    private String status;

    public TerminalCommand() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getSessionId() { return sessionId; }
    public void setSessionId(Integer sessionId) { this.sessionId = sessionId; }
    public Integer getDeviceId() { return deviceId; }
    public void setDeviceId(Integer deviceId) { this.deviceId = deviceId; }
    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }
    public Integer getMessageId() { return messageId; }
    public void setMessageId(Integer messageId) { this.messageId = messageId; }
    public Long getSentAt() { return sentAt; }
    public void setSentAt(Long sentAt) { this.sentAt = sentAt; }
    public Long getCompletedAt() { return completedAt; }
    public void setCompletedAt(Long completedAt) { this.completedAt = completedAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
