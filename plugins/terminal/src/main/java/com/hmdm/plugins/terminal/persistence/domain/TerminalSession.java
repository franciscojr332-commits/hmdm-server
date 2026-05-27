package com.hmdm.plugins.terminal.persistence.domain;

public class TerminalSession {
    private Integer id;
    private Integer customerId;
    private Integer userId;
    private Long startedAt;
    private Long endedAt;
    private String label;

    public TerminalSession() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public Integer getCustomerId() { return customerId; }
    public void setCustomerId(Integer customerId) { this.customerId = customerId; }
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public Long getStartedAt() { return startedAt; }
    public void setStartedAt(Long startedAt) { this.startedAt = startedAt; }
    public Long getEndedAt() { return endedAt; }
    public void setEndedAt(Long endedAt) { this.endedAt = endedAt; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
