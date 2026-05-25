/*
 * HMDM-EVOLUTION F1.5: Reconciliation task.
 *
 * Periodic job that detects:
 *   1. Commands stuck in IN_FLIGHT for > 5min without ACK (server marked sent but
 *      device never confirmed receipt).
 *   2. Commands past their expires_at (will be purged eventually by MessagePurgeWorker).
 *
 * In F1, this task is OBSERVE-ONLY — it logs and audits but does not reset state,
 * because there is no ACK protocol yet (F2). Resetting status=1 → status=0 without
 * ACK guarantee could cause re-execution on the device.
 *
 * In F2, when delivered_ack_at column starts being populated by agent ACK,
 * this task will be extended to reset stale IN_FLIGHT back to ENQUEUED for retry.
 */

package com.hmdm.notification.task;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.notification.persistence.CommandAuditDAO;
import com.hmdm.notification.persistence.domain.CommandAuditEvent;
import com.hmdm.notification.persistence.mapper.NotificationMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Singleton
public class CommandReconciliationTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CommandReconciliationTask.class);

    public static final long STALE_THRESHOLD_MS = 5 * 60 * 1000L;

    private final NotificationMapper mapper;
    private final CommandAuditDAO auditDAO;

    @Inject
    public CommandReconciliationTask(NotificationMapper mapper, CommandAuditDAO auditDAO) {
        this.mapper = mapper;
        this.auditDAO = auditDAO;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        long staleBoundary = now - STALE_THRESHOLD_MS;
        try {
            int staleCount = checkStaleInFlight(staleBoundary);
            int expiredCount = checkExpired(now);
            if (staleCount > 0 || expiredCount > 0) {
                log.info("Reconciliation cycle: stale_in_flight={} expired={}", staleCount, expiredCount);
            } else {
                log.debug("Reconciliation cycle: nothing to reconcile");
            }
        } catch (Exception e) {
            log.error("Reconciliation cycle failed", e);
        }
    }

    private int checkStaleInFlight(long staleBoundary) {
        // HMDM-EVOLUTION F2 DUAL MODE:
        //   Legacy devices (agent_supports_ack=FALSE) — OBSERVE-ONLY, no reset
        //   F2+ devices (agent_supports_ack=TRUE) — RESET status=1→0 for retry

        // BRANCH 1: legacy devices — log + audit only (zero impact)
        List<Integer> legacyStaleIds = mapper.findStaleInFlightIds(staleBoundary);
        for (Integer messageId : legacyStaleIds) {
            log.warn("Stale IN_FLIGHT (legacy device, no reset): messageId={} no_ack_for_{}ms",
                    messageId, STALE_THRESHOLD_MS);
            auditDAO.logEvent(messageId,
                    CommandAuditEvent.EVENT_RECONCILIATION,
                    CommandAuditEvent.STATE_IN_FLIGHT,
                    "IN_FLIGHT_STALE",
                    "system:reconciliation",
                    "{\"reason\":\"no_delivery_ack_within_threshold\",\"threshold_ms\":" +
                        STALE_THRESHOLD_MS + ",\"reset\":false}",
                    null);
        }

        // BRANCH 2: F2 devices — reset to ENQUEUED for retry
        List<Integer> resetableIds = mapper.findResetableStaleInFlightIds(staleBoundary);
        int resetCount = 0;
        for (Integer messageId : resetableIds) {
            log.warn("Stale IN_FLIGHT (F2 device, resetting): messageId={}", messageId);
            int reset = mapper.resetStaleInFlight(messageId);
            if (reset > 0) {
                resetCount++;
                auditDAO.logEvent(messageId,
                        CommandAuditEvent.EVENT_RECONCILIATION,
                        CommandAuditEvent.STATE_IN_FLIGHT,
                        "IN_FLIGHT_STALE_RESET",
                        "system:reconciliation",
                        "{\"reason\":\"stale_in_flight_reset\",\"threshold_ms\":" +
                            STALE_THRESHOLD_MS + ",\"reset\":true}",
                        null);
            } else {
                // Race condition: ACK arrived between SELECT and UPDATE
                auditDAO.logEvent(messageId,
                        CommandAuditEvent.EVENT_RECONCILIATION,
                        CommandAuditEvent.STATE_IN_FLIGHT,
                        "IN_FLIGHT_RACE_ACKED",
                        "system:reconciliation",
                        "{\"reason\":\"ack_arrived_during_reset\"}",
                        null);
            }
        }

        return legacyStaleIds.size() + resetCount;
    }

    private int checkExpired(long now) {
        List<Integer> expiredIds = mapper.findExpiredIds(now);
        for (Integer messageId : expiredIds) {
            log.warn("Expired command detected: messageId={}", messageId);
            auditDAO.logTransition(messageId, null,
                    CommandAuditEvent.STATE_EXPIRED,
                    "system:reconciliation",
                    "{\"reason\":\"expires_at_passed\"}");
        }
        return expiredIds.size();
    }
}
