package com.hmdm.plugins.terminal.rest.json;

import java.util.List;
import java.util.Map;

/**
 * Reply for /private/exec — maps deviceId -> list of pushmessages.id created.
 */
public class TerminalExecResponse {
    private Integer sessionId;
    private Map<Integer, List<Integer>> deviceCommands;
    private int totalEnqueued;
    private boolean dryRun;

    public TerminalExecResponse() {}

    public TerminalExecResponse(Integer sessionId, Map<Integer, List<Integer>> deviceCommands, int totalEnqueued, boolean dryRun) {
        this.sessionId = sessionId;
        this.deviceCommands = deviceCommands;
        this.totalEnqueued = totalEnqueued;
        this.dryRun = dryRun;
    }

    public Integer getSessionId() { return sessionId; }
    public void setSessionId(Integer sessionId) { this.sessionId = sessionId; }
    public Map<Integer, List<Integer>> getDeviceCommands() { return deviceCommands; }
    public void setDeviceCommands(Map<Integer, List<Integer>> deviceCommands) { this.deviceCommands = deviceCommands; }
    public int getTotalEnqueued() { return totalEnqueued; }
    public void setTotalEnqueued(int totalEnqueued) { this.totalEnqueued = totalEnqueued; }
    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }
}
