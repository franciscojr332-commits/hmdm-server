/*
 * HMDM-EVOLUTION F1.3: Audit event for command lifecycle.
 *
 * Append-only record of state transitions per command (pushMessage).
 * Persisted in mdm_command_audit table.
 */

package com.hmdm.notification.persistence.domain;

import java.io.Serializable;

public class CommandAuditEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String EVENT_STATE_TRANSITION = "STATE_TRANSITION";
    public static final String EVENT_RETRY = "RETRY";
    public static final String EVENT_ERROR = "ERROR";
    public static final String EVENT_ACK_DELIVERY = "ACK_DELIVERY";
    public static final String EVENT_ACK_EXECUTION = "ACK_EXECUTION";
    public static final String EVENT_RECONCILIATION = "RECONCILIATION";

    public static final String STATE_CREATED = "CREATED";
    public static final String STATE_ENQUEUED = "ENQUEUED";
    public static final String STATE_IN_FLIGHT = "IN_FLIGHT";
    public static final String STATE_DELIVERED = "DELIVERED";
    public static final String STATE_EXECUTED = "EXECUTED";
    public static final String STATE_FAILED = "FAILED";
    public static final String STATE_EXPIRED = "EXPIRED";

    private Long id;
    private Integer messageId;
    private String eventType;
    private String fromState;
    private String toState;
    private Long eventAt;
    private String actor;
    private String details;
    private String ipAddress;

    public CommandAuditEvent() {}

    public CommandAuditEvent(Integer messageId, String eventType, String fromState, String toState, String actor) {
        this.messageId = messageId;
        this.eventType = eventType;
        this.fromState = fromState;
        this.toState = toState;
        this.actor = actor;
        this.eventAt = System.currentTimeMillis();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Integer getMessageId() { return messageId; }
    public void setMessageId(Integer messageId) { this.messageId = messageId; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getFromState() { return fromState; }
    public void setFromState(String fromState) { this.fromState = fromState; }

    public String getToState() { return toState; }
    public void setToState(String toState) { this.toState = toState; }

    public Long getEventAt() { return eventAt; }
    public void setEventAt(Long eventAt) { this.eventAt = eventAt; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }

    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
}
