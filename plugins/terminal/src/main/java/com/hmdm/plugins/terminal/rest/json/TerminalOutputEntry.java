package com.hmdm.plugins.terminal.rest.json;

/**
 * One row of console output streamed from devices.
 */
public class TerminalOutputEntry {
    private Long id;
    private Integer deviceId;
    private String deviceNumber;
    private Long ts;
    private String severity;
    private String message;
    private String kind;

    public TerminalOutputEntry() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getDeviceId() { return deviceId; }
    public void setDeviceId(Integer deviceId) { this.deviceId = deviceId; }
    public String getDeviceNumber() { return deviceNumber; }
    public void setDeviceNumber(String deviceNumber) { this.deviceNumber = deviceNumber; }
    public Long getTs() { return ts; }
    public void setTs(Long ts) { this.ts = ts; }
    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
}
