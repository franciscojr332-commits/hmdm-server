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

package com.hmdm.notification.guice.module;

import com.google.inject.Inject;
import com.hmdm.notification.persistence.NotificationDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * <p>A module used for starting the runnable tasks for <code>Notification</code> sub-system.</p>
 *
 * @author isv
 */
public class NotificationTaskModule {

    private final ScheduledExecutorService messagePurgeService = Executors.newScheduledThreadPool(1);

    private final NotificationDAO notificationDAO;

    /**
     * <p>Constructs new <code>NotificationTaskModule</code> instance. This implementation does nothing.</p>
     */
    @Inject
    public NotificationTaskModule(NotificationDAO notificationDAO) {
        this.notificationDAO = notificationDAO;
    }

    public void init() {
        messagePurgeService.scheduleWithFixedDelay(new MessagePurgeWorker(notificationDAO),
                1, 1, TimeUnit.HOURS);

        Runtime.getRuntime().addShutdownHook(new Thread(messagePurgeService::shutdown));
    }

    /**
     * <p>A task to delete the push messages with lifespans longer than pre-defined limits.</p>
     */
    public static class MessagePurgeWorker implements Runnable {
        private final static Logger log = LoggerFactory.getLogger(MessagePurgeWorker.class);
        private final NotificationDAO notificationDAO;

        public MessagePurgeWorker(NotificationDAO notificationDAO) {
            this.notificationDAO = notificationDAO;
        }

        // HMDM-EVOLUTION F0.1: non-delivered TTL raised from 1h to 7 days
        // to survive weekend offline periods. Pair with reconciliation job (F1) for retry coverage.
        private static final int NON_DELIVERED_LIFESPAN_SEC = 7 * 24 * 3600;
        private static final int DELIVERED_LIFESPAN_SEC = 7 * 24 * 3600;

        @Override
        public void run() {
            log.info("Starting the iteration ...");
            try {
                this.notificationDAO.purgeMessages(NON_DELIVERED_LIFESPAN_SEC, DELIVERED_LIFESPAN_SEC);
                log.info("Finished the iteration.");
            } catch (Exception e) {
                log.error("Unexpected error when purging the push messages", e);
            }
        }
    }

}
