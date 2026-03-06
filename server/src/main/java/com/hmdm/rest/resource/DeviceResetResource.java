/*
 *
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hmdm.rest.resource;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.PUT;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import com.hmdm.notification.PushService;
import com.hmdm.notification.persistence.domain.PushMessage;
import com.hmdm.persistence.DeviceDAO;
import com.hmdm.rest.json.DeviceInfo;
import com.hmdm.rest.json.Response;
import com.hmdm.security.SecurityContext;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.Authorization;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Device reset (factory reset) and confirm endpoints.
 * Android app expects paths under /rest/plugins/devicereset/.
 */
@Api(tags = {"Device Reset"}, authorizations = {@Authorization("Bearer Token")})
@Singleton
@Path("/plugins/devicereset")
public class DeviceResetResource {

    private static final Logger log = LoggerFactory.getLogger(DeviceResetResource.class);

    private final DeviceDAO deviceDAO;
    private final PushService pushService;

    @Inject
    public DeviceResetResource(DeviceDAO deviceDAO, PushService pushService) {
        this.deviceDAO = deviceDAO;
        this.pushService = pushService;
    }

    /**
     * Admin requests factory reset for a device.
     * <ul>
     *   <li><b>Servidor:</b> 1) Marca flag pending_factory_reset = true no BD. 2) Após 10s envia push tipo configUpdated para o dispositivo.</li>
     *   <li><b>configUpdated:</b> Quem chama é o servidor (thread agendada em scheduleConfigUpdatedPushAfterDelay), 10s depois do clique no botão.</li>
     *   <li><b>Wipe (reset de fábrica):</b> Quem executa é o app Android no dispositivo: ao receber o push configUpdated, o device faz sync; a resposta do sync traz factoryReset=true; o app então executa o wipe.</li>
     * </ul>
     * Supports both: PUT /private/reset/{deviceId} (preferred) and PUT /private/reset with body { deviceId }.
     */
    @ApiOperation(value = "Request device factory reset (by path)")
    @PUT
    @Path("/private/reset/{deviceId}")
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response requestDeviceResetByPath(@PathParam("deviceId") String deviceIdStr) {
        Integer deviceId = null;
        if (deviceIdStr != null && !deviceIdStr.trim().isEmpty()) {
            try {
                deviceId = Integer.valueOf(deviceIdStr.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid deviceId in path: {}", deviceIdStr);
                return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST)
                        .entity(Response.ERROR("error.bad.request"))
                        .build();
            }
        }
        if (deviceId == null) {
            return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST)
                    .entity(Response.ERROR("error.bad.request"))
                    .build();
        }
        Response result = doRequestDeviceReset(deviceId);
        return javax.ws.rs.core.Response.ok(result).build();
    }

    /** Legacy: body or query param. Accepts deviceId as String to avoid 500 when ?deviceId= (empty); returns 400 when invalid. */
    @ApiOperation(value = "Request device factory reset (by body or query, legacy)")
    @PUT
    @Path("/private/reset")
    @Produces(MediaType.APPLICATION_JSON)
    public javax.ws.rs.core.Response requestDeviceResetByBody(
            DeviceResetRequest request,
            @QueryParam("deviceId") String queryDeviceIdStr) {
        Integer deviceId = null;
        if (request != null && request.getDeviceId() != null) {
            deviceId = request.getDeviceId();
        }
        if (deviceId == null && queryDeviceIdStr != null && !queryDeviceIdStr.trim().isEmpty()) {
            try {
                deviceId = Integer.valueOf(queryDeviceIdStr.trim());
            } catch (NumberFormatException e) {
                log.warn("Invalid deviceId in query: {}", queryDeviceIdStr);
                return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST)
                        .entity(Response.ERROR("error.bad.request"))
                        .build();
            }
        }
        if (deviceId == null) {
            return javax.ws.rs.core.Response.status(javax.ws.rs.core.Response.Status.BAD_REQUEST)
                    .entity(Response.ERROR("error.bad.request"))
                    .build();
        }
        Response result = doRequestDeviceReset(deviceId);
        return javax.ws.rs.core.Response.ok(result).build();
    }

    /** Delay in milliseconds before sending configUpdated push after setting reset flag (10 seconds). */
    private static final long RESET_PUSH_DELAY_MS = 10_000L;

    private Response doRequestDeviceReset(Integer deviceId) {
        if (!SecurityContext.get().hasPermission("plugin_devicereset_access")
                && !SecurityContext.get().hasPermission("edit_devices")) {
            return Response.PERMISSION_DENIED();
        }
        if (deviceId == null) {
            return Response.ERROR("error.bad.request");
        }
        try {
            deviceDAO.requestDeviceReset(deviceId);
            log.info("Device reset requested for device id {}, pending_factory_reset set to true; configUpdated push in {}s", deviceId, RESET_PUSH_DELAY_MS / 1000);
            scheduleConfigUpdatedPushAfterDelay(deviceId);
            return Response.OK();
        } catch (Exception e) {
            log.error("Failed to request device reset for device id {}", deviceId, e);
            return Response.INTERNAL_ERROR();
        }
    }

    /**
     * Sends configUpdated push to the device after a delay so the flag is committed and the device receives the push with the reset instruction.
     */
    private void scheduleConfigUpdatedPushAfterDelay(final Integer deviceId) {
        final PushService push = this.pushService;
        new Thread(() -> {
            try {
                Thread.sleep(RESET_PUSH_DELAY_MS);
                boolean pushSent = push.sendSimpleMessage(deviceId, PushMessage.TYPE_CONFIG_UPDATED);
                if (pushSent) {
                    log.info("Device id {}: configUpdated push sent after delay", deviceId);
                } else {
                    log.warn("Device {} not found for push; reset flag was set, device will get it on next sync", deviceId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Reset push delay interrupted for device id {}", deviceId);
            }
        }, "device-reset-push-" + deviceId).start();
    }

    /** Request body for legacy PUT /private/reset (no path param). */
    public static class DeviceResetRequest {
        private Integer deviceId;
        public Integer getDeviceId() { return deviceId; }
        public void setDeviceId(Integer deviceId) { this.deviceId = deviceId; }
    }

    /**
     * Device confirms before wiping (called by Android app). Clears pending reset flag.
     */
    @ApiOperation(value = "Device confirms factory reset (public)")
    @POST
    @Path("/public/{number}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response confirmDeviceReset(
            @PathParam("number") @ApiParam("Device number") String number,
            DeviceInfo deviceInfo) {
        try {
            deviceDAO.clearPendingFactoryResetByNumber(number);
            return Response.OK();
        } catch (Exception e) {
            log.error("Failed to clear pending reset for device {}", number, e);
            return Response.INTERNAL_ERROR();
        }
    }

}
