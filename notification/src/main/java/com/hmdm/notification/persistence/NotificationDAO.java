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

package com.hmdm.notification.persistence;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.hmdm.notification.persistence.domain.CommandAuditEvent;
import com.hmdm.notification.persistence.domain.PushMessage;
import com.hmdm.notification.persistence.mapper.NotificationMapper;
import org.mybatis.guice.transactional.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>An interface to notification messages persistence.</p>
 *
 * @author isv
 */
@Singleton
public class NotificationDAO {

    private static final Logger log = LoggerFactory.getLogger(NotificationDAO.class);

    private final NotificationMapper notificationMapper;
    private final CommandAuditDAO auditDAO;

    /**
     * <p>Constructs new <code>NotificationDAO</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public NotificationDAO(NotificationMapper notificationMapper, CommandAuditDAO auditDAO) {
        this.notificationMapper = notificationMapper;
        this.auditDAO = auditDAO;
    }

    /**
     * <p>Gets the list of messages to be delivered to specified device. The returned messages are immediately marked as
     * delivered.</p>
     *
     * @param deviceNumber a device number identifying the device.
     * @return a list of messages to be delivered to device.
     */
    @Transactional
    public List<PushMessage> getPendingMessagesForDelivery(String deviceNumber) {
        final List<PushMessage> messages = this.notificationMapper.getPendingMessagesByNumber(deviceNumber);
        if (!messages.isEmpty()) {
            final List<Integer> messageIds = messages.stream().map(PushMessage::getId).collect(Collectors.toList());
            this.notificationMapper.markMessagesAsDelivered(messageIds);
            // HMDM-EVOLUTION F1.4: audit transition ENQUEUED → IN_FLIGHT
            for (PushMessage m : messages) {
                auditDAO.logTransition(m.getId(),
                        CommandAuditEvent.STATE_ENQUEUED,
                        CommandAuditEvent.STATE_IN_FLIGHT,
                        "system:polling:" + deviceNumber);
            }
        }
        return messages;
    }

    /**
     * <p>Gets the list of messages to be delivered to specified device. The returned messages are immediately marked as
     * delivered.</p>
     *
     * @param deviceId a device id in the database.
     * @return a list of messages to be delivered to device.
     */
    @Transactional
    public List<PushMessage> getPendingMessagesForDelivery(int deviceId) {
        final List<PushMessage> messages = this.notificationMapper.getPendingMessagesById(deviceId);
        if (!messages.isEmpty()) {
            final List<Integer> messageIds = messages.stream().map(PushMessage::getId).collect(Collectors.toList());
            this.notificationMapper.markMessagesAsDelivered(messageIds);
            // HMDM-EVOLUTION F1.4: audit transition ENQUEUED → IN_FLIGHT
            for (PushMessage m : messages) {
                auditDAO.logTransition(m.getId(),
                        CommandAuditEvent.STATE_ENQUEUED,
                        CommandAuditEvent.STATE_IN_FLIGHT,
                        "system:polling:deviceId=" + deviceId);
            }
        }
        return messages;
    }

    /**
     * <p>Sends the specified notification message. This implementation puts it to queue to be retrieved by device later.</p>
     *
     * @param message a message to send.
     * @return an ID of a message.
     */
    @Transactional
    public int send(PushMessage message) {
        this.notificationMapper.insertPushMessage(message);
        this.notificationMapper.insertPendingPush(message.getId());
        // HMDM-EVOLUTION F1.4 + F1.7: audit transition CREATED → ENQUEUED + MDC
        try (MDC.MDCCloseable mdc1 = MDC.putCloseable("commandId", String.valueOf(message.getId()));
             MDC.MDCCloseable mdc2 = MDC.putCloseable("deviceId", String.valueOf(message.getDeviceId()))) {
            log.info("Command persisted type={} deviceId={}", message.getMessageType(), message.getDeviceId());
            auditDAO.logTransition(message.getId(),
                    CommandAuditEvent.STATE_CREATED,
                    CommandAuditEvent.STATE_ENQUEUED,
                    "system:send",
                    "{\"messageType\":\"" + message.getMessageType() + "\"}");
        }
        return message.getId();
    }

    /**
     * <p>Gets the current status of delivery for the specified message.</p>
     *
     * @param messageId an ID of a message to get status for.
     * @return a status of message delivery. 0 - not sent, 1 - sent to device; or <code>null</code> if specified message
     *         is not found.
     */
    public Integer getStatus(int messageId) {
        return this.notificationMapper.getDeliveryStatus(messageId);
    }

    /**
     * <p>Deletes the messages with lifespans exceeding the specified limits.</p>
     *
     * @param nonDeliveredMessagesLifeSpan a limit for lifespan for non-delivered messages (in seconds).
     * @param deliveredMessagesLifeSpan a limit for lifespan for delivered messages (in seconds).
     */
    @Transactional
    public void purgeMessages(int nonDeliveredMessagesLifeSpan, int deliveredMessagesLifeSpan) {
        // HMDM-EVOLUTION F1.4: count purges for visibility (do not audit each — could be thousands)
        // Future: insert audit row per purged messageId if business needs it
        this.notificationMapper.purgeMessages(nonDeliveredMessagesLifeSpan * 1000L, deliveredMessagesLifeSpan * 1000L);
    }
}
