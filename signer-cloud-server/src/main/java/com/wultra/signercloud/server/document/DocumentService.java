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
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * Document service.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Service
@AllArgsConstructor
@Slf4j
@Transactional
class DocumentService {

    private final DocumentConfigurationProperties configurationProperties;

    private final DocumentRepository documentRepository;

    /**
     * Cleanup documents.
     *
     * @return statistics of the cleanup operation
     * @implNote This method is intended to be called by a scheduled job to clean up
     */
    CleanupResult cleanupDocuments() {
        final Instant now = Instant.now();
        final CleanupResult.CleanupResultBuilder cleanupResultBuilder = CleanupResult.builder();

        processRetention(
                DocumentStatus.REJECTED,
                configurationProperties.getRejected().getRetentionPeriod(),
                cleanupResultBuilder::rejectedDocuments,
                now
        );

        processRetention(
                DocumentStatus.SIGNED,
                configurationProperties.getSigned().getRetentionPeriod(),
                cleanupResultBuilder::signedDocuments,
                now
        );

        processRetention(
                DocumentStatus.WAITING,
                configurationProperties.getWaiting().getRetentionPeriod(),
                cleanupResultBuilder::waitingDocuments,
                now
        );

        return cleanupResultBuilder.build();
    }

    private void processRetention(final DocumentStatus status, final Duration retentionPeriod, final Consumer<String> resultConsumer, final Instant now) {
        if (retentionPeriod != null) {
            long deletedCount = documentRepository.deleteByStatusAndTimestampCreatedBefore(status, now.minus(retentionPeriod));
            resultConsumer.accept(String.valueOf(deletedCount));
        } else {
            resultConsumer.accept("disabled");
        }
    }

    @Builder
    record CleanupResult(String rejectedDocuments, String signedDocuments, String waitingDocuments) {
    }
}
