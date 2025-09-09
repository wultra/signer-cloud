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

import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.RejectedExecutionException;

/**
 * Service for enqueueing Callback Events for further processing.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Service
@Slf4j
@AllArgsConstructor
public class CallbackQueueService {

    private final CallbackService callbackService;

    private final ThreadPoolTaskExecutor callbackEventsThreadPoolExecutor;

    /**
     * Move all Callback Events from the Executor's queue to the database queue on graceful shutdown.
     */
    @PreDestroy
    private void clearExecutorQueue() {
        logger.info("Moving Callback URL Events from executor's queue to PENDING state.");
        callbackEventsThreadPoolExecutor.getThreadPoolExecutor().shutdownNow().stream()
                .filter(CallbackEventRunnable.class::isInstance)
                .map(CallbackEventRunnable.class::cast)
                .forEach(CallbackEventRunnable::cancel);
    }

    /**
     * Submit Callback Event to be dispatched by a task executor as soon as possible.
     *
     * @param callbackUrlEvent Callback Event to submit.
     * @throws RejectedExecutionException In case the Callback Event could not be submitted.
     */
    public void submitToExecutor(final CallbackEventData callbackUrlEvent) throws RejectedExecutionException {
        final CallbackEventRunnable runnable = CallbackEventRunnable.builder()
                .dispatchAction(() -> callbackService.dispatchInstantCallbackEvent(callbackUrlEvent))
                .cancelAction(() -> callbackService.moveCallbackEventToPending(callbackUrlEvent))
                .build();

        callbackEventsThreadPoolExecutor.execute(runnable);
    }

    /**
     * Enqueue a Callback Event to the database to be dispatched by a job.
     *
     * @param callbackEvent Callback Event to enqueue.
     */
    public void enqueueToDatabase(final CallbackEventData callbackEvent) {
        callbackService.moveCallbackEventToPending(callbackEvent);
    }
}
