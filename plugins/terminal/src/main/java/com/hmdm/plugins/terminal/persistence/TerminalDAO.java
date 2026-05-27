package com.hmdm.plugins.terminal.persistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.plugins.terminal.persistence.domain.TerminalCommand;
import com.hmdm.plugins.terminal.persistence.domain.TerminalSession;
import com.hmdm.plugins.terminal.persistence.domain.TerminalSnippet;
import com.hmdm.plugins.terminal.persistence.mapper.TerminalMapper;
import com.hmdm.plugins.terminal.rest.json.TerminalDeviceStatus;
import com.hmdm.plugins.terminal.rest.json.TerminalOutputEntry;

import java.util.List;
import java.util.Map;

@Singleton
public class TerminalDAO {

    private final TerminalMapper mapper;

    @Inject
    public TerminalDAO(TerminalMapper mapper) {
        this.mapper = mapper;
    }

    // ─── Sessions ───
    public TerminalSession startSession(int customerId, int userId, String label) {
        TerminalSession s = new TerminalSession();
        s.setCustomerId(customerId);
        s.setUserId(userId);
        s.setStartedAt(System.currentTimeMillis());
        s.setLabel(label != null ? label : "default");
        mapper.insertSession(s);
        return s;
    }

    public void endSession(int sessionId) {
        mapper.endSession(sessionId, System.currentTimeMillis());
    }

    // ─── Commands ───
    public void recordCommand(int sessionId, int deviceId, String command,
                               String messageType, int messageId, String status) {
        TerminalCommand c = new TerminalCommand();
        c.setSessionId(sessionId);
        c.setDeviceId(deviceId);
        c.setCommand(command);
        c.setMessageType(messageType);
        c.setMessageId(messageId);
        c.setSentAt(System.currentTimeMillis());
        c.setStatus(status);
        mapper.insertCommand(c);
    }

    public void markCommandDone(int messageId, String status) {
        mapper.updateCommandStatus(messageId, status, System.currentTimeMillis());
    }

    public List<TerminalCommand> listSessionCommands(int sessionId) {
        return mapper.getCommandsBySession(sessionId);
    }

    // ─── Output ───
    public List<TerminalOutputEntry> readOutputSince(long sinceMs, List<Integer> deviceIds, int maxRows) {
        return mapper.getOutputSince(sinceMs, deviceIds, maxRows);
    }

    // ─── Devices ───
    public List<TerminalDeviceStatus> listDevices(int customerId, Integer configurationId) {
        return mapper.getDevices(customerId, configurationId);
    }

    // ─── Snippets ───
    public List<TerminalSnippet> listSnippets(int customerId) {
        return mapper.getSnippets(customerId);
    }

    public TerminalSnippet createSnippet(TerminalSnippet snippet) {
        snippet.setCreatedAt(System.currentTimeMillis());
        mapper.insertSnippet(snippet);
        return snippet;
    }

    public void updateSnippet(TerminalSnippet snippet) {
        mapper.updateSnippet(snippet);
    }

    public void deleteSnippet(int id, int customerId) {
        mapper.deleteSnippet(id, customerId);
    }

    // ─── Favorites ───
    public List<Integer> listFavorites(int userId) {
        return mapper.getFavoriteSnippetIds(userId);
    }

    public void favorite(int userId, int snippetId) {
        mapper.addFavorite(userId, snippetId);
    }

    public void unfavorite(int userId, int snippetId) {
        mapper.removeFavorite(userId, snippetId);
    }

    // ─── Status batch ───
    public List<Map<String, Object>> getStatus(List<Integer> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) return java.util.Collections.emptyList();
        return mapper.getMessagesStatus(messageIds);
    }

    // ─── History ───
    public List<Map<String, Object>> searchHistory(int customerId, Integer userId,
                                                    Integer deviceId, Long sinceMs,
                                                    String search, int limit) {
        return mapper.searchHistory(customerId, userId, deviceId, sinceMs, search, Math.min(limit, 500));
    }

    public int purgeOlderThan(int days) {
        long cutoff = System.currentTimeMillis() - ((long) days * 24L * 60L * 60L * 1000L);
        int commands = mapper.purgeOldCommands(cutoff);
        mapper.purgeOldSessions(cutoff);
        return commands;
    }
}
