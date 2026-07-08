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

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * A scheduled job that performs cleanup operations on documents.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Component
@AllArgsConstructor
@Slf4j
class DocumentCleanupJob {

    private final DocumentService documentService;

    @Scheduled(cron = "${signer-cloud.server.document.cleanup.cron}", zone = "UTC")
    @SchedulerLock(name = "cleanupDocuments")
    public void cleanupDocuments() {
        logger.info("Cleanup documents initiated", kv("action", "cleanupDocuments"), kv("state", "initiated"));
        LockAssert.assertLocked();
        final var result = documentService.cleanupDocuments();
        logger.info("Cleanup documents succeeded", kv("action", "cleanupDocuments"), kv("state", "succeeded"), kv("count", result));
    }
}
