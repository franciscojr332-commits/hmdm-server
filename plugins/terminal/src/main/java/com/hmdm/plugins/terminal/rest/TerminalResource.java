package com.hmdm.plugins.terminal.rest;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.notification.PushService;
import com.hmdm.notification.persistence.domain.PushMessage;
import com.hmdm.persistence.DeviceDAO;
import com.hmdm.persistence.domain.Device;
import com.hmdm.plugins.terminal.persistence.TerminalDAO;
import com.hmdm.plugins.terminal.persistence.domain.TerminalSession;
import com.hmdm.plugins.terminal.persistence.domain.TerminalSnippet;
import com.hmdm.plugins.terminal.rest.json.TerminalDeviceStatus;
import com.hmdm.plugins.terminal.rest.json.TerminalExecRequest;
import com.hmdm.plugins.terminal.rest.json.TerminalExecResponse;
import com.hmdm.plugins.terminal.rest.json.TerminalOutputEntry;
import com.hmdm.rest.json.Response;
import com.hmdm.security.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.util.*;

@Singleton
@Path("/plugins/terminal")
public class TerminalResource {

    private static final Logger log = LoggerFactory.getLogger(TerminalResource.class);

    private static final String PERM_USE = "plugin_terminal_use";
    private static final String PERM_DESTRUCTIVE = "plugin_terminal_destructive";

    private DeviceDAO deviceDAO;
    private PushService pushService;
    private TerminalDAO terminalDAO;

    /**
     * No-arg constructor required by Jersey/HK2 reification (matches the
     * pattern used by AuditResource, PushResource, MessagingResource).
     * Without it, HK2 fails with NoSuchMethodException and the endpoint
     * does not respond, so the devices picker stays empty in the UI.
     */
    public TerminalResource() {
    }

    @Inject
    public TerminalResource(DeviceDAO deviceDAO, PushService pushService, TerminalDAO terminalDAO) {
        this.deviceDAO = deviceDAO;
        this.pushService = pushService;
        this.terminalDAO = terminalDAO;
    }

    // ─── Health ──────────────────────────────────────────────────────────
    @GET
    @Path("/private/ping")
    @Produces(MediaType.APPLICATION_JSON)
    public Response ping() {
        return Response.OK("terminal plugin up");
    }

    // ─── Devices list for picker ─────────────────────────────────────────
    @GET
    @Path("/private/devices")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listDevices(@QueryParam("configurationId") Integer configurationId) {
        if (!SecurityContext.get().hasPermission(PERM_USE)) return Response.PERMISSION_DENIED();
        try {
            int customerId = SecurityContext.get().getCurrentCustomerId().orElseThrow(IllegalStateException::new);
            List<TerminalDeviceStatus> rows = terminalDAO.listDevices(customerId, configurationId);
            long now = System.currentTimeMillis();
            for (TerminalDeviceStatus s : rows) {
                s.setOnline(s.getLastPollMs() != null && (now - s.getLastPollMs()) < 120_000L);
            }
            return Response.OK(rows);
        } catch (Exception e) {
            log.error("Failed to list devices", e);
            return Response.ERROR(e.getMessage());
        }
    }

    // ─── Open session ────────────────────────────────────────────────────
    @POST
    @Path("/private/sessions")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response openSession(Map<String, Object> body) {
        if (!SecurityContext.get().hasPermission(PERM_USE)) return Response.PERMISSION_DENIED();
        try {
            int customerId = SecurityContext.get().getCurrentCustomerId().orElseThrow(IllegalStateException::new);
            int userId = SecurityContext.get().getCurrentUser().get().getId();
            String label = body != null && body.get("label") != null ? String.valueOf(body.get("label")) : "session";
            TerminalSession s = terminalDAO.startSession(customerId, userId, label);
            return Response.OK(s);
        } catch (Exception e) {
            log.error("Failed to open session", e);
            return Response.ERROR(e.getMessage());
        }
    }

    @POST
    @Path("/private/sessions/{id}/close")
    @Produces(MediaType.APPLICATION_JSON)
    public Response closeSession(@PathParam("id") int sessionId) {
        if (!SecurityContext.get().hasPermission(PERM_USE)) return Response.PERMISSION_DENIED();
        try {
            terminalDAO.endSession(sessionId);
            return Response.OK();
        } catch (Exception e) {
            log.error("Failed to close session", e);
            return Response.ERROR(e.getMessage());
        }
    }

    // ─── Exec command(s) on device(s) ────────────────────────────────────
    @POST
    @Path("/private/exec")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response exec(TerminalExecRequest req) {
        if (!SecurityContext.get().hasPermission(PERM_USE)) return Response.PERMISSION_DENIED();
        if (req.isDestructive() && !SecurityContext.get().hasPermission(PERM_DESTRUCTIVE)) {
            return Response.PERMISSION_DENIED();
        }
        try {
            if (req.getCommands() == null || req.getCommands().isEmpty()) {
                return Response.ERROR("no commands provided");
            }

            List<Device> targets = resolveDevices(req);
            if (targets.isEmpty()) return Response.ERROR("no devices resolved for scope");

            String msgType = req.getMessageType() != null && !req.getMessageType().isEmpty()
                    ? req.getMessageType() : "runCommand";

            Map<Integer, List<Integer>> deviceCommands = new LinkedHashMap<>();
            int totalEnqueued = 0;

            if (req.isDryRun()) {
                for (Device d : targets) deviceCommands.put(d.getId(), Collections.emptyList());
                return Response.OK(new TerminalExecResponse(req.getSessionId(), deviceCommands, 0, true));
            }

            for (Device d : targets) {
                List<Integer> ids = new ArrayList<>();
                for (String cmd : req.getCommands()) {
                    String payload = buildPayload(msgType, cmd);
                    PushMessage pm = new PushMessage();
                    pm.setDeviceId(d.getId());
                    pm.setMessageType(msgType);
                    if (payload != null) pm.setPayload(payload);
                    pushService.send(pm);
                    Integer pmId = pm.getId();
                    if (pmId != null) {
                        ids.add(pmId);
                        if (req.getSessionId() != null) {
                            terminalDAO.recordCommand(req.getSessionId(), d.getId(), cmd, msgType, pmId, "SENT");
                        }
                        totalEnqueued++;
                    }
                }
                deviceCommands.put(d.getId(), ids);
            }
            if (req.isDestructive()) {
                String user = SecurityContext.get().getCurrentUser().get().getLogin();
                log.warn("Terminal DESTRUCTIVE exec: user={} type={} devices={} commands={} total={} cmds={}",
                        user, msgType, targets.size(), req.getCommands().size(), totalEnqueued,
                        String.join(" | ", req.getCommands()));
            } else {
                log.info("Terminal exec: type={} devices={} commands={} total={}",
                        msgType, targets.size(), req.getCommands().size(), totalEnqueued);
            }
            return Response.OK(new TerminalExecResponse(req.getSessionId(), deviceCommands, totalEnqueued, false));
        } catch (Exception e) {
            log.error("Terminal exec failure", e);
            return Response.ERROR(e.getMessage());
        }
    }

    // ─── Output poll ─────────────────────────────────────────────────────
    @GET
    @Path("/private/output")
    @Produces(MediaType.APPLICATION_JSON)
    public Response output(@QueryParam("since") Long sinceMs,
                            @QueryParam("deviceIds") String deviceIdsCsv,
                            @QueryParam("max") Integer max) {
        if (!SecurityContext.get().hasPermission(PERM_USE)) return Response.PERMISSION_DENIED();
        try {
            if (sinceMs == null) sinceMs = System.currentTimeMillis() - 60_000L;
            int limit = max != null ? Math.min(max, 500) : 200;
            List<Integer> deviceIds = parseDeviceIds(deviceIdsCsv);
            List<TerminalOutputEntry> rows = terminalDAO.readOutputSince(sinceMs, deviceIds, limit);
            return Response.OK(rows);
        } catch (Exception e) {
            log.error("Output query failure", e);
            return Response.ERROR(e.getMessage());
        }
    }

    // ─── Snippets CRUD ───────────────────────────────────────────────────
    @GET
    @Path("/private/snippets")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listSnippets() {
        if (!SecurityContext.get().hasPermission(PERM_USE)) return Response.PERMISSION_DENIED();
        try {
            int customerId = SecurityContext.get().getCurrentCustomerId().orElseThrow(IllegalStateException::new);
            return Response.OK(terminalDAO.listSnippets(customerId));
        } catch (Exception e) {
            log.error("Snippets list failure", e);
            return Response.ERROR(e.getMessage());
        }
    }

    @POST
    @Path("/private/snippets")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response createSnippet(TerminalSnippet snippet) {
        if (!SecurityContext.get().hasPermission(PERM_USE)) return Response.PERMISSION_DENIED();
        try {
            int customerId = SecurityContext.get().getCurrentCustomerId().orElseThrow(IllegalStateException::new);
            int userId = SecurityContext.get().getCurrentUser().get().getId();
            snippet.setCustomerId(customerId);
            snippet.setCreatedBy(userId);
            return Response.OK(terminalDAO.createSnippet(snippet));
        } catch (Exception e) {
            log.error("Snippet create failure", e);
            return Response.ERROR(e.getMessage());
        }
    }

    @PUT
    @Path("/private/snippets/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response updateSnippet(@PathParam("id") int id, TerminalSnippet snippet) {
        if (!SecurityContext.get().hasPermission(PERM_USE)) return Response.PERMISSION_DENIED();
        try {
            int customerId = SecurityContext.get().getCurrentCustomerId().orElseThrow(IllegalStateException::new);
            snippet.setId(id);
            snippet.setCustomerId(customerId);
            terminalDAO.updateSnippet(snippet);
            return Response.OK();
        } catch (Exception e) {
            log.error("Snippet update failure", e);
            return Response.ERROR(e.getMessage());
        }
    }

    @DELETE
    @Path("/private/snippets/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteSnippet(@PathParam("id") int id) {
        if (!SecurityContext.get().hasPermission(PERM_USE)) return Response.PERMISSION_DENIED();
        try {
            int customerId = SecurityContext.get().getCurrentCustomerId().orElseThrow(IllegalStateException::new);
            terminalDAO.deleteSnippet(id, customerId);
            return Response.OK();
        } catch (Exception e) {
            log.error("Snippet delete failure", e);
            return Response.ERROR(e.getMessage());
        }
    }

    // ─── History ─────────────────────────────────────────────────────────
    @GET
    @Path("/private/history")
    @Produces(MediaType.APPLICATION_JSON)
    public Response history(@QueryParam("deviceId") Integer deviceId,
                             @QueryParam("since") Long sinceMs,
                             @QueryParam("search") String search,
                             @QueryParam("limit") Integer limit,
                             @QueryParam("scope") String scope) {
        if (!SecurityContext.get().hasPermission(PERM_USE)) return Response.PERMISSION_DENIED();
        try {
            int customerId = SecurityContext.get().getCurrentCustomerId().orElseThrow(IllegalStateException::new);
            Integer userId = null;
            if ("self".equals(scope) || scope == null) {
                userId = SecurityContext.get().getCurrentUser().get().getId();
            }
            int lim = limit != null ? Math.min(limit, 500) : 200;
            return Response.OK(terminalDAO.searchHistory(customerId, userId, deviceId, sinceMs, search, lim));
        } catch (Exception e) {
            log.error("History query failure", e);
            return Response.ERROR(e.getMessage());
        }
    }

    @DELETE
    @Path("/private/purge/{days}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response purge(@PathParam("days") int days) {
        if (!SecurityContext.get().hasPermission(PERM_DESTRUCTIVE)) return Response.PERMISSION_DENIED();
        try {
            if (days < 1) return Response.ERROR("days must be >= 1");
            int removed = terminalDAO.purgeOlderThan(days);
            return Response.OK("purged " + removed + " commands older than " + days + " days");
        } catch (Exception e) {
            log.error("Purge failure", e);
            return Response.ERROR(e.getMessage());
        }
    }

    // ─── Status batch ────────────────────────────────────────────────────
    @GET
    @Path("/private/status")
    @Produces(MediaType.APPLICATION_JSON)
    public Response status(@QueryParam("messageIds") String idsCsv) {
        if (!SecurityContext.get().hasPermission(PERM_USE)) return Response.PERMISSION_DENIED();
        try {
            List<Integer> ids = parseDeviceIds(idsCsv);
            return Response.OK(terminalDAO.getStatus(ids));
        } catch (Exception e) {
            log.error("Status query failure", e);
            return Response.ERROR(e.getMessage());
        }
    }

    // ─── Favorites ───────────────────────────────────────────────────────
    @GET
    @Path("/private/favorites")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listFavorites() {
        if (!SecurityContext.get().hasPermission(PERM_USE)) return Response.PERMISSION_DENIED();
        try {
            int userId = SecurityContext.get().getCurrentUser().get().getId();
            return Response.OK(terminalDAO.listFavorites(userId));
        } catch (Exception e) {
            log.error("Favorites list failure", e);
            return Response.ERROR(e.getMessage());
        }
    }

    @POST
    @Path("/private/favorites/{snippetId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response addFavorite(@PathParam("snippetId") int snippetId) {
        if (!SecurityContext.get().hasPermission(PERM_USE)) return Response.PERMISSION_DENIED();
        try {
            int userId = SecurityContext.get().getCurrentUser().get().getId();
            terminalDAO.favorite(userId, snippetId);
            return Response.OK();
        } catch (Exception e) {
            log.error("Favorite add failure", e);
            return Response.ERROR(e.getMessage());
        }
    }

    @DELETE
    @Path("/private/favorites/{snippetId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response removeFavorite(@PathParam("snippetId") int snippetId) {
        if (!SecurityContext.get().hasPermission(PERM_USE)) return Response.PERMISSION_DENIED();
        try {
            int userId = SecurityContext.get().getCurrentUser().get().getId();
            terminalDAO.unfavorite(userId, snippetId);
            return Response.OK();
        } catch (Exception e) {
            log.error("Favorite remove failure", e);
            return Response.ERROR(e.getMessage());
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────
    private List<Device> resolveDevices(TerminalExecRequest req) {
        List<Device> result = new ArrayList<>();
        if ("devices".equals(req.getScope()) && req.getDeviceIds() != null) {
            for (Integer id : req.getDeviceIds()) {
                Device d = deviceDAO.getDeviceById(id);
                if (d != null) result.add(d);
            }
        } else if ("configuration".equals(req.getScope()) && req.getConfigurationId() != null) {
            result.addAll(deviceDAO.getDeviceIdsByConfigurationId(req.getConfigurationId()));
        }
        return result;
    }

    private String buildPayload(String msgType, String cmd) {
        if (cmd == null) return null;
        switch (msgType) {
            case "runCommand":
                return "{\"command\":\"" + escapeJson(cmd) + "\"}";
            case "grantPermissions":
                return "{\"pkg\":\"" + escapeJson(cmd) + "\"}";
            default:
                String t = cmd.trim();
                if (t.startsWith("{")) return cmd;
                return "{\"command\":\"" + escapeJson(cmd) + "\"}";
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        return sb.toString();
    }

    private List<Integer> parseDeviceIds(String csv) {
        if (csv == null || csv.isEmpty()) return Collections.emptyList();
        List<Integer> out = new ArrayList<>();
        for (String p : csv.split(",")) {
            try { out.add(Integer.parseInt(p.trim())); } catch (NumberFormatException ignore) {}
        }
        return out;
    }
}
