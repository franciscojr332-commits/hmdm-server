package com.hmdm.plugins.terminal.rest.json;

import java.util.List;

/**
 * Body for POST /rest/plugins/terminal/private/exec
 *
 * scope: "devices" | "configuration" | "group"
 *   - "devices"        → deviceIds[] used
 *   - "configuration"  → configurationId used
 *   - "group"          → groupId used
 *
 * commands: each entry is sent as one push (in order).
 * messageType: defaults to "runCommand"; pass "grantPermissions" for native push.
 * dryRun: validate only, don't enqueue.
 */
public class TerminalExecRequest {

    private String scope;
    private List<Integer> deviceIds;
    private Integer configurationId;
    private Integer groupId;
    private List<String> commands;
    private String messageType;
    private boolean destructive;
    private boolean dryRun;
    private Integer sessionId;

    public TerminalExecRequest() {
    }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public List<Integer> getDeviceIds() { return deviceIds; }
    public void setDeviceIds(List<Integer> deviceIds) { this.deviceIds = deviceIds; }

    public Integer getConfigurationId() { return configurationId; }
    public void setConfigurationId(Integer configurationId) { this.configurationId = configurationId; }

    public Integer getGroupId() { return groupId; }
    public void setGroupId(Integer groupId) { this.groupId = groupId; }

    public List<String> getCommands() { return commands; }
    public void setCommands(List<String> commands) { this.commands = commands; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public boolean isDestructive() { return destructive; }
    public void setDestructive(boolean destructive) { this.destructive = destructive; }

    public boolean isDryRun() { return dryRun; }
    public void setDryRun(boolean dryRun) { this.dryRun = dryRun; }

    public Integer getSessionId() { return sessionId; }
    public void setSessionId(Integer sessionId) { this.sessionId = sessionId; }
}
