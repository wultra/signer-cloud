/*
 * Signer Cloud
 * Copyright (C) 2025 Wultra s.r.o.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.wultra.signercloud.server.document;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * A scheduled job that performs cleanup operations on documents.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Component
@AllArgsConstructor
@Slf4j
class CleanupJob {

    private final DocumentConfigurationProperties configurationProperties;

    private final DocumentRepository documentRepository;

    // TODO Lubos make single method
    @Scheduled(cron = "${signer-cloud.server.document.waiting.cron:0 0/10 * * * *}", zone = "UTC")
    @SchedulerLock(name = "cleanupWaitingDocuments")
    public void cleanupWaitingDocuments() {
        logger.info("action: cleanupWaitingDocuments, state: initiated");
        LockAssert.assertLocked();
        // TODO Lubos
        logger.info("action: cleanupWaitingDocuments, state: succeeded, size: {}", 0);
    }

    @Scheduled(cron = "${signer-cloud.server.document.rejected.cron:0 3/10 * * * *}", zone = "UTC")
    @SchedulerLock(name = "cleanupRejectedDocuments")
    public void cleanupRejectedDocuments() {
        logger.info("action: cleanupRejectedDocuments, state: initiated");
        LockAssert.assertLocked();
        // TODO Lubos
        logger.info("action: cleanupRejectedDocuments, state: succeeded, size: {}", 0);
    }

    @Scheduled(cron = "${signer-cloud.server.document.signed.cron:0 6/10 * * * *}", zone = "UTC")
    @SchedulerLock(name = "cleanupSignedDocuments")
    public void cleanupSignedDocuments() {
        logger.info("action: cleanupSignedDocuments, state: initiated");
        LockAssert.assertLocked();
        // TODO Lubos
        logger.info("action: cleanupSignedDocuments, state: succeeded, size: {}", 0);
    }
}
