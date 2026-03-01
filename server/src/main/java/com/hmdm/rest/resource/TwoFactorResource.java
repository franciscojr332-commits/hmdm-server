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

import com.hmdm.persistence.CommonDAO;
import com.hmdm.persistence.UnsecureDAO;
import com.hmdm.persistence.UserDAO;
import com.hmdm.persistence.domain.Settings;
import com.hmdm.persistence.domain.User;
import com.hmdm.rest.json.Response;
import com.hmdm.security.SecurityContext;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import net.glxn.qrgen.core.image.ImageType;
import net.glxn.qrgen.javase.QRCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response as JaxRsResponse;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * REST resource for two-factor authentication (TOTP / Google Authenticator).
 */
@Api(tags = {"Two-factor authentication"})
@Singleton
@Path("/private/twofactor")
public class TwoFactorResource {

    private static final Logger logger = LoggerFactory.getLogger(TwoFactorResource.class);
    private static final String ISSUER = "HeadwindMDM";
    private static final int QR_SIZE = 300;

    private final UserDAO userDAO;
    private final UnsecureDAO unsecureDAO;
    private final CommonDAO commonDAO;
    private final SecretGenerator secretGenerator;
    private final DefaultCodeVerifier codeVerifier;

    public TwoFactorResource() {
        this.userDAO = null;
        this.unsecureDAO = null;
        this.commonDAO = null;
        this.secretGenerator = null;
        this.codeVerifier = null;
    }

    @Inject
    public TwoFactorResource(UserDAO userDAO, UnsecureDAO unsecureDAO, CommonDAO commonDAO) {
        this.userDAO = userDAO;
        this.unsecureDAO = unsecureDAO;
        this.commonDAO = commonDAO;
        this.secretGenerator = new DefaultSecretGenerator();
        this.codeVerifier = new DefaultCodeVerifier(
                new DefaultCodeGenerator(HashingAlgorithm.SHA1, 6),
                new SystemTimeProvider()
        );
    }

    @GET
    @Path("/qr/{userId}")
    @Produces("image/png")
    public JaxRsResponse getQrCode(@PathParam("userId") @ApiParam("User ID") int userId) {
        Optional<User> current = SecurityContext.get().getCurrentUser();
        if (!current.isPresent()) {
            return JaxRsResponse.status(JaxRsResponse.SC_UNAUTHORIZED).build();
        }
        User me = current.get();
        if (me.getId() != userId && !me.getUserRole().isSuperAdmin()) {
            return JaxRsResponse.status(JaxRsResponse.SC_FORBIDDEN).build();
        }
        User user = userDAO.getUserDetails(userId);
        if (user == null) {
            return JaxRsResponse.status(JaxRsResponse.SC_NOT_FOUND).build();
        }
        try {
            String secret = secretGenerator.generate();
            user.setTwoFactorSecret(secret);
            user.setTwoFactorAccepted(false);
            userDAO.updateUserMainDetails(user);

            String label = ISSUER + ":" + (user.getEmail() != null && !user.getEmail().isEmpty() ? user.getEmail() : user.getLogin());
            String encodedLabel = URLEncoder.encode(label, StandardCharsets.UTF_8.name());
            String otpauth = String.format("otpauth://totp/%s?secret=%s&issuer=%s",
                    encodedLabel, secret, URLEncoder.encode(ISSUER, StandardCharsets.UTF_8.name()));

            ByteArrayOutputStream os = new ByteArrayOutputStream();
            QRCode.from(otpauth).withSize(QR_SIZE, QR_SIZE).to(ImageType.PNG).writeTo(os);
            byte[] png = os.toByteArray();

            return JaxRsResponse.ok(png)
                    .type("image/png")
                    .build();
        } catch (Exception e) {
            logger.error("Failed to generate 2FA QR code for user {}", userId, e);
            return JaxRsResponse.serverError().build();
        }
    }

    @GET
    @Path("/verify/{user}/{code}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response verify(@PathParam("user") @ApiParam("User ID") int userId,
                           @PathParam("code") @ApiParam("TOTP code") String code) {
        Optional<User> current = SecurityContext.get().getCurrentUser();
        if (!current.isPresent()) {
            return Response.PERMISSION_DENIED();
        }
        User me = current.get();
        if (me.getId() != userId && !me.getUserRole().isSuperAdmin()) {
            return Response.PERMISSION_DENIED();
        }
        User user = userDAO.getUserDetails(userId);
        if (user == null || user.getTwoFactorSecret() == null || user.getTwoFactorSecret().isEmpty()) {
            return Response.ERROR("error.twofactor.no.secret");
        }
        if (code == null || code.length() != 6 || !code.matches("\\d+")) {
            return Response.ERROR("error.twofactor.invalid.code");
        }
        try {
            if (!codeVerifier.isValidCode(user.getTwoFactorSecret(), code)) {
                return Response.ERROR("error.twofactor.invalid.code");
            }
            return Response.OK();
        } catch (Exception e) {
            logger.error("2FA verify failed for user {}", userId, e);
            return Response.INTERNAL_ERROR();
        }
    }

    @GET
    @Path("/set")
    @Produces(MediaType.APPLICATION_JSON)
    public Response set() {
        Optional<User> current = SecurityContext.get().getCurrentUser();
        if (!current.isPresent()) {
            return Response.PERMISSION_DENIED();
        }
        User user = current.get();
        if (user.getTwoFactorSecret() == null || user.getTwoFactorSecret().isEmpty()) {
            return Response.ERROR("error.twofactor.no.secret");
        }
        try {
            user.setTwoFactorAccepted(true);
            userDAO.updateUserMainDetails(user);

            Settings settings = unsecureDAO.getSettings(user.getCustomerId());
            if (settings != null) {
                settings.setTwoFactor(true);
                commonDAO.setTwoFactor(settings);
            }
            return Response.OK();
        } catch (Exception e) {
            logger.error("2FA set failed for user {}", user.getId(), e);
            return Response.INTERNAL_ERROR();
        }
    }

    @GET
    @Path("/reset")
    @Produces(MediaType.APPLICATION_JSON)
    public Response reset() {
        Optional<User> current = SecurityContext.get().getCurrentUser();
        if (!current.isPresent()) {
            return Response.PERMISSION_DENIED();
        }
        User user = current.get();
        try {
            user.setTwoFactorSecret(null);
            user.setTwoFactorAccepted(false);
            userDAO.updateUserMainDetails(user);

            Settings settings = unsecureDAO.getSettings(user.getCustomerId());
            if (settings != null) {
                settings.setTwoFactor(false);
                commonDAO.setTwoFactor(settings);
            }
            return Response.OK();
        } catch (Exception e) {
            logger.error("2FA reset failed for user {}", user.getId(), e);
            return Response.INTERNAL_ERROR();
        }
    }
}
