/*
 * HMDM-EVOLUTION F2: REST endpoints for device-to-server ACK protocol.
 *
 * Endpoints (public — protected by IP filter + optional HMAC signature):
 *   POST /rest/notification/ack/delivery
 *   POST /rest/notification/ack/execution
 *   POST /rest/notification/ack/batch
 *
 * Idempotent: re-sending same ACK returns 200 with no extra audit row.
 * Ownership: messageId must belong to deviceNumber's device.
 */

package com.hmdm.notification.rest;

import com.hmdm.notification.persistence.CommandAuditDAO;
import com.hmdm.notification.persistence.domain.CommandAuditEvent;
import com.hmdm.notification.persistence.mapper.NotificationMapper;
import com.hmdm.notification.rest.json.AckBatchRequest;
import com.hmdm.notification.rest.json.AckDeliveryRequest;
import com.hmdm.notification.rest.json.AckExecutionRequest;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.domain.Device;
import com.hmdm.rest.filter.PublicIPFilter;
import com.hmdm.rest.json.Response;
import com.hmdm.util.CryptoUtil;
import org.mybatis.guice.transactional.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;

@Singleton
@Path("/notification/ack")
public class AckResource {

    private static final Logger log = LoggerFactory.getLogger(AckResource.class);

    private NotificationMapper mapper;
    private CommandAuditDAO auditDAO;
    private UnsecureDAO unsecureDAO;
    private PublicIPFilter publicIPFilter;
    private String hashSecret;
    private boolean secureEnrollment;

    /**
     * Empty constructor required by Jersey/Swagger reification.
     */
    public AckResource() {
    }

    @Inject
    public AckResource(NotificationMapper mapper,
                       CommandAuditDAO auditDAO,
                       UnsecureDAO unsecureDAO,
                       PublicIPFilter publicIPFilter,
                       @Named("secure.enrollment") boolean secureEnrollment,
                       @Named("hash.secret") String hashSecret) {
        this.mapper = mapper;
        this.auditDAO = auditDAO;
        this.unsecureDAO = unsecureDAO;
        this.publicIPFilter = publicIPFilter;
        this.hashSecret = hashSecret;
        this.secureEnrollment = secureEnrollment;
    }

    @POST
    @Path("/delivery")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response ackDelivery(@Context HttpServletRequest req,
                                 @HeaderParam("X-Request-Signature") String signature,
                                 AckDeliveryRequest body) {
        if (req != null && !publicIPFilter.match(req)) {
            return Response.PERMISSION_DENIED();
        }
        if (body == null || body.getMessageId() == null || body.getDeviceNumber() == null) {
            return Response.ERROR("missing required fields");
        }
        if (secureEnrollment && !validateSignature(signature, "/rest/notification/ack/delivery", body.getDeviceNumber())) {
            log.warn("ACK delivery: signature mismatch device={}", body.getDeviceNumber());
            return Response.PERMISSION_DENIED();
        }
        if (!isOwner(body.getDeviceNumber(), body.getMessageId())) {
            log.warn("ACK delivery: device {} not owner of messageId {}", body.getDeviceNumber(), body.getMessageId());
            return Response.PERMISSION_DENIED();
        }

        try (MDC.MDCCloseable mdc1 = MDC.putCloseable("commandId", String.valueOf(body.getMessageId()));
             MDC.MDCCloseable mdc2 = MDC.putCloseable("deviceNumber", body.getDeviceNumber())) {

            long ackAt = body.getReceivedAt() != null ? body.getReceivedAt() : System.currentTimeMillis();
            int updated = mapper.markDelivered(body.getMessageId(), ackAt);

            if (updated > 0) {
                auditDAO.logEvent(body.getMessageId(),
                        CommandAuditEvent.EVENT_ACK_DELIVERY,
                        CommandAuditEvent.STATE_IN_FLIGHT,
                        CommandAuditEvent.STATE_DELIVERED,
                        "device:" + body.getDeviceNumber(),
                        "{\"received_at\":" + ackAt + "}",
                        getRemoteAddr(req));
                log.info("Delivery ACK accepted commandId={} device={}", body.getMessageId(), body.getDeviceNumber());
            } else {
                log.debug("Delivery ACK idempotent (already acked) commandId={}", body.getMessageId());
            }

            return Response.OK();
        }
    }

    @POST
    @Path("/execution")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Transactional
    public Response ackExecution(@Context HttpServletRequest req,
                                  @HeaderParam("X-Request-Signature") String signature,
                                  AckExecutionRequest body) {
        if (req != null && !publicIPFilter.match(req)) {
            return Response.PERMISSION_DENIED();
        }
        if (body == null || body.getMessageId() == null
                || body.getDeviceNumber() == null || body.getStatus() == null) {
            return Response.ERROR("missing required fields");
        }
        if (secureEnrollment && !validateSignature(signature, "/rest/notification/ack/execution", body.getDeviceNumber())) {
            return Response.PERMISSION_DENIED();
        }
        if (!isOwner(body.getDeviceNumber(), body.getMessageId())) {
            return Response.PERMISSION_DENIED();
        }

        try (MDC.MDCCloseable mdc1 = MDC.putCloseable("commandId", String.valueOf(body.getMessageId()));
             MDC.MDCCloseable mdc2 = MDC.putCloseable("deviceNumber", body.getDeviceNumber())) {

            long ackAt = body.getExecutedAt() != null ? body.getExecutedAt() : System.currentTimeMillis();

            if (AckExecutionRequest.STATUS_OK.equalsIgnoreCase(body.getStatus())) {
                int updated = mapper.markExecuted(body.getMessageId(), ackAt);
                if (updated > 0) {
                    auditDAO.logEvent(body.getMessageId(),
                            CommandAuditEvent.EVENT_ACK_EXECUTION,
                            CommandAuditEvent.STATE_DELIVERED,
                            CommandAuditEvent.STATE_EXECUTED,
                            "device:" + body.getDeviceNumber(),
                            null,
                            getRemoteAddr(req));
                    log.info("Execution OK ACK commandId={} device={}",
                            body.getMessageId(), body.getDeviceNumber());
                }
            } else if (AckExecutionRequest.STATUS_FAILED.equalsIgnoreCase(body.getStatus())) {
                int updated = mapper.markFailed(body.getMessageId(), ackAt,
                        body.getFailureCode(), body.getFailureMessage());
                if (updated > 0) {
                    String details = "{\"failure_code\":\"" + safeJson(body.getFailureCode()) +
                                     "\",\"failure_message\":\"" + safeJson(body.getFailureMessage()) + "\"}";
                    auditDAO.logEvent(body.getMessageId(),
                            CommandAuditEvent.EVENT_ACK_EXECUTION,
                            CommandAuditEvent.STATE_DELIVERED,
                            CommandAuditEvent.STATE_FAILED,
                            "device:" + body.getDeviceNumber(),
                            details,
                            getRemoteAddr(req));
                    log.warn("Execution FAILED ACK commandId={} device={} code={} msg={}",
                            body.getMessageId(), body.getDeviceNumber(),
                            body.getFailureCode(), body.getFailureMessage());
                }
            } else {
                return Response.ERROR("invalid status: " + body.getStatus());
            }

            return Response.OK();
        }
    }

    @POST
    @Path("/batch")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response ackBatch(@Context HttpServletRequest req,
                              @HeaderParam("X-Request-Signature") String signature,
                              AckBatchRequest body) {
        if (body == null || body.getDeviceNumber() == null) {
            return Response.ERROR("missing device");
        }
        int processed = 0;
        if (body.getDeliveries() != null) {
            for (AckDeliveryRequest d : body.getDeliveries()) {
                if (d.getDeviceNumber() == null) {
                    d.setDeviceNumber(body.getDeviceNumber());
                }
                ackDelivery(req, signature, d);
                processed++;
            }
        }
        if (body.getExecutions() != null) {
            for (AckExecutionRequest e : body.getExecutions()) {
                if (e.getDeviceNumber() == null) {
                    e.setDeviceNumber(body.getDeviceNumber());
                }
                ackExecution(req, signature, e);
                processed++;
            }
        }
        return Response.OK("processed=" + processed);
    }

    private boolean isOwner(String deviceNumber, int messageId) {
        Integer ownerDeviceId = mapper.findDeviceIdByMessageId(messageId);
        if (ownerDeviceId == null) {
            return false;
        }
        Device device = unsecureDAO.getDeviceByNumber(deviceNumber);
        if (device == null) {
            device = unsecureDAO.getDeviceByOldNumber(deviceNumber);
        }
        return device != null && device.getId() == ownerDeviceId.intValue();
    }

    private boolean validateSignature(String signature, String path, String deviceNumber) {
        if (signature == null) {
            return false;
        }
        try {
            String fullPath = path + "/" + deviceNumber;
            String good = CryptoUtil.getSHA1String(hashSecret + fullPath);
            return signature.equalsIgnoreCase(good);
        } catch (Exception e) {
            return false;
        }
    }

    private String getRemoteAddr(HttpServletRequest req) {
        return req != null ? req.getRemoteAddr() : null;
    }

    private String safeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
