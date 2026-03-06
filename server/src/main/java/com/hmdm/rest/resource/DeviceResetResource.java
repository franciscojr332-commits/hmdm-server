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
import javax.ws.rs.core.MediaType;

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

    @Inject
    public DeviceResetResource(DeviceDAO deviceDAO) {
        this.deviceDAO = deviceDAO;
    }

    /**
     * Admin requests factory reset for a device. Next sync will send factoryReset flag to the device.
     */
    @ApiOperation(value = "Request device factory reset")
    @PUT
    @Path("/private/reset")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response requestDeviceReset(DeviceResetRequest request) {
        if (!SecurityContext.get().hasPermission("plugin_devicereset_access")) {
            return Response.PERMISSION_DENIED();
        }
        if (request == null || request.getDeviceId() == null) {
            return Response.BAD_REQUEST();
        }
        try {
            deviceDAO.requestDeviceReset(request.getDeviceId());
            return Response.OK();
        } catch (Exception e) {
            log.error("Failed to request device reset for device id {}", request.getDeviceId(), e);
            return Response.INTERNAL_ERROR();
        }
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

    /** Request body for private/reset: deviceId to reset. */
    public static class DeviceResetRequest {
        private Integer deviceId;

        public Integer getDeviceId() {
            return deviceId;
        }

        public void setDeviceId(Integer deviceId) {
            this.deviceId = deviceId;
        }
    }
}
