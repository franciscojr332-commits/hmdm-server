/*
 * HMDM-EVOLUTION F1.6: View model for a single audit event in command timeline.
 */

package com.hmdm.notification.rest.json;

import com.hmdm.notification.persistence.domain.CommandAuditEvent;

import java.io.Serializable;

public class CommandAuditView implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Integer messageId;
    private String eventType;
    private String fromState;
    private String toState;
    private Long eventAt;
    private String actor;
    private String details;
    private String ipAddress;

    public CommandAuditView() {}

    public CommandAuditView(CommandAuditEvent e) {
        this.id = e.getId();
        this.messageId = e.getMessageId();
        this.eventType = e.getEventType();
        this.fromState = e.getFromState();
        this.toState = e.getToState();
        this.eventAt = e.getEventAt();
        this.actor = e.getActor();
        this.details = e.getDetails();
        this.ipAddress = e.getIpAddress();
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
