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
package com.wultra.signercloud.server.callback;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.core.LockAssert;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * A scheduled job that performs cleanup operations on signers.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Component
@AllArgsConstructor
@Slf4j
class CallbackJob {

    private final CallbackService callbackService;

    private final CallbackConfigurationProperties configurationProperties;

    @Scheduled(cron = "${signer-cloud.server.callback.dispatch-pending-callback-events.job.cron}", zone = "UTC")
    @SchedulerLock(name = "dispatchPendingCallbackEvents")
    public void dispatchPendingCallbackEvents() {
        final int limit = configurationProperties.getDispatchPendingCallbackEvents().job().limit();
        logger.info("Dispatch pending callback events initiated", kv("action", "dispatchPendingCallbackEvents"), kv("state", "initiated"), kv("limit", limit));
        LockAssert.assertLocked();
        final var result = callbackService.dispatchPendingCallbackEvents(limit);
        logger.info("Dispatch pending callback events succeeded", kv("action", "dispatchPendingCallbackEvents"), kv("state", "succeeded"), kv("count", result));
    }

    @Scheduled(cron = "${signer-cloud.server.callback.cleanup-callback-events.job.cron}", zone = "UTC")
    @SchedulerLock(name = "cleanupCallbackEvents")
    public void cleanupCallbackEvents() {
        logger.info("Cleanup callback events initiated", kv("action", "cleanupCallbackEvents"), kv("state", "initiated"));
        LockAssert.assertLocked();
        final var result = callbackService.deleteCallbackEventsAfterRetentionPeriod();
        logger.info("Cleanup callback events succeeded", kv("action", "cleanupCallbackEvents"), kv("state", "succeeded"), kv("count", result));
    }

    @Scheduled(cron = "${signer-cloud.server.callback.rerun-stale-callback-events.job.cron}", zone = "UTC")
    @SchedulerLock(name = "rerunStaleCallbackEvents")
    public void rerunStaleCallbackEvents() {
        logger.info("Rerun stale callback events initiated", kv("action", "rerunStaleCallbackEvents"), kv("state", "initiated"));
        LockAssert.assertLocked();
        final var result = callbackService.resetStaleCallbackEvents();
        logger.info("Rerun stale callback events succeeded", kv("action", "rerunStaleCallbackEvents"), kv("state", "succeeded"), kv("count", result));
    }
}
