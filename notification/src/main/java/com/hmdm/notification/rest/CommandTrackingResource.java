/*
 * HMDM-EVOLUTION F1.6: REST resource for command tracking / audit timeline.
 *
 * Endpoints (under /rest/private to be protected by auth filter):
 *   GET /rest/private/notifications/commands
 *   GET /rest/private/notifications/commands/{messageId}
 *   GET /rest/private/notifications/commands/stats
 */

package com.hmdm.notification.rest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.notification.persistence.CommandAuditDAO;
import com.hmdm.notification.persistence.domain.CommandAuditEvent;
import com.hmdm.notification.persistence.mapper.NotificationMapper;
import com.hmdm.notification.rest.json.CommandAuditView;
import com.hmdm.notification.rest.json.CommandStateView;
import com.hmdm.rest.json.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
@Path("/private/notifications/commands")
public class CommandTrackingResource {

    private static final Logger log = LoggerFactory.getLogger(CommandTrackingResource.class);

    private final NotificationMapper mapper;
    private final CommandAuditDAO auditDAO;

    @Inject
    public CommandTrackingResource(NotificationMapper mapper, CommandAuditDAO auditDAO) {
        this.mapper = mapper;
        this.auditDAO = auditDAO;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response listCommands(@QueryParam("deviceId") Integer deviceId,
                                  @QueryParam("state") String state,
                                  @QueryParam("messageType") String messageType,
                                  @QueryParam("since") Long since,
                                  @QueryParam("limit") Integer limitParam,
                                  @QueryParam("offset") Integer offsetParam) {
        try {
            int limit = (limitParam == null || limitParam < 1 || limitParam > 500) ? 50 : limitParam;
            int offset = (offsetParam == null || offsetParam < 0) ? 0 : offsetParam;

            List<CommandStateView> commands = mapper.findCommands(deviceId, state, messageType, since, limit, offset);
            return Response.OK(commands);
        } catch (Exception e) {
            log.error("Failed to list commands", e);
            return Response.INTERNAL_ERROR();
        }
    }

    @GET
    @Path("/{messageId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCommand(@PathParam("messageId") int messageId) {
        try {
            CommandStateView cmd = mapper.findCommandById(messageId);
            if (cmd == null) {
                return Response.ERROR("Command not found");
            }
            List<CommandAuditEvent> audit = auditDAO.findByMessageId(messageId);
            cmd.setAuditTrail(audit.stream().map(CommandAuditView::new).collect(Collectors.toList()));
            return Response.OK(cmd);
        } catch (Exception e) {
            log.error("Failed to get command {}", messageId, e);
            return Response.INTERNAL_ERROR();
        }
    }

    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getStats(@QueryParam("since") Long since,
                              @QueryParam("deviceId") Integer deviceId) {
        try {
            List<Map<String, Object>> raw = mapper.countByState(since, deviceId);
            Map<String, Long> stats = new HashMap<>();
            // ENQUEUED, IN_FLIGHT, DELIVERED, EXECUTED, FAILED, EXPIRED
            stats.put("ENQUEUED", 0L);
            stats.put("IN_FLIGHT", 0L);
            stats.put("DELIVERED", 0L);
            stats.put("EXECUTED", 0L);
            stats.put("FAILED", 0L);
            stats.put("EXPIRED", 0L);
            for (Map<String, Object> row : raw) {
                Object stateObj = row.get("state");
                Object countObj = row.get("count");
                if (stateObj != null && countObj != null) {
                    stats.put(stateObj.toString(), ((Number) countObj).longValue());
                }
            }
            return Response.OK(stats);
        } catch (Exception e) {
            log.error("Failed to get stats", e);
            return Response.INTERNAL_ERROR();
        }
    }
}
