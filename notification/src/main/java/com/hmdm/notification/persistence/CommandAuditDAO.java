/*
 * HMDM-EVOLUTION F1.3: DAO for command audit events.
 * Wraps CommandAuditMapper. Best-effort persistence (catch exceptions to avoid disrupting main flow).
 */

package com.hmdm.notification.persistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.notification.persistence.domain.CommandAuditEvent;
import com.hmdm.notification.persistence.mapper.CommandAuditMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Singleton
public class CommandAuditDAO {

    private static final Logger log = LoggerFactory.getLogger(CommandAuditDAO.class);

    private final CommandAuditMapper mapper;

    @Inject
    public CommandAuditDAO(CommandAuditMapper mapper) {
        this.mapper = mapper;
    }

    public void logTransition(int messageId, String fromState, String toState, String actor) {
        logEvent(messageId, CommandAuditEvent.EVENT_STATE_TRANSITION, fromState, toState, actor, null, null);
    }

    public void logTransition(int messageId, String fromState, String toState, String actor, String details) {
        logEvent(messageId, CommandAuditEvent.EVENT_STATE_TRANSITION, fromState, toState, actor, details, null);
    }

    public void logEvent(int messageId, String eventType, String fromState, String toState,
                          String actor, String details, String ipAddress) {
        try {
            CommandAuditEvent event = new CommandAuditEvent();
            event.setMessageId(messageId);
            event.setEventType(eventType);
            event.setFromState(fromState);
            event.setToState(toState);
            event.setEventAt(System.currentTimeMillis());
            event.setActor(actor);
            event.setDetails(details);
            event.setIpAddress(ipAddress);
            mapper.insertEvent(event);
        } catch (Exception e) {
            log.warn("Failed to persist command audit event for messageId={}: {}", messageId, e.getMessage());
        }
    }

    public List<CommandAuditEvent> findByMessageId(int messageId) {
        return mapper.findByMessageId(messageId);
    }

    public long countSince(long since) {
        return mapper.countSince(since);
    }
}
