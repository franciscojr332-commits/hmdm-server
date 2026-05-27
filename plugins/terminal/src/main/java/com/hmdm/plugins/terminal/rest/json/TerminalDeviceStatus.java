package com.hmdm.plugins.terminal.rest.json;

/**
 * Status snapshot of a single device for the Terminal UI.
 */
public class TerminalDeviceStatus {
    private Integer id;
    private String number;
    private String description;
    private Integer configurationId;
    private String configurationName;
    private Long lastPollMs;
    private boolean online;
    private Integer pendingCount;

    public TerminalDeviceStatus() {}

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getNumber() { return number; }
    public void setNumber(String number) { this.number = number; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Integer getConfigurationId() { return configurationId; }
    public void setConfigurationId(Integer configurationId) { this.configurationId = configurationId; }
    public String getConfigurationName() { return configurationName; }
    public void setConfigurationName(String configurationName) { this.configurationName = configurationName; }
    public Long getLastPollMs() { return lastPollMs; }
    public void setLastPollMs(Long lastPollMs) { this.lastPollMs = lastPollMs; }
    public boolean isOnline() { return online; }
    public void setOnline(boolean online) { this.online = online; }
    public Integer getPendingCount() { return pendingCount; }
    public void setPendingCount(Integer pendingCount) { this.pendingCount = pendingCount; }
}
