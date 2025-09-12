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

import com.wultra.signercloud.server.callback.api.CallbackNotificationService;
import com.wultra.signercloud.server.callback.api.CallbackType;
import com.wultra.signercloud.server.utils.TransactionUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.RejectedExecutionException;

/**
 * Implementation of {@link CallbackNotificationService}.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Service
@AllArgsConstructor
@Slf4j
class CallbackNotificationServiceInternal implements CallbackNotificationService {

    private final CallbackService callbackService;
    private final CallbackQueueService callbackQueueService;
    private final CallbackConvertor callbackConvertor;

    @Override
    public void notify(final CallbackType callbackType, final String callbackData) {
        if (callbackService.failureThresholdReached(callbackType)) {
            logger.warn("Callback has reached failure threshold, associated events are not dispatched: callbackType={}", callbackType);
            callbackService.createAndSaveFailedEvent(callbackType, callbackData);
            return;
        }

        final CallbackEvent callbackEvent = callbackService.createAndSaveEventForProcessing(callbackType, callbackData);
        final CallbackEventData callbackEventData = callbackConvertor.convert(callbackEvent);
        TransactionUtils.executeAfterTransactionCommits(() -> enqueue(callbackEventData));
    }

    /**
     * Try to submit a Callback Event to a task executor. If rejected, enqueue the Callback Event to a database.
     * @param callbackEventData Callback Event to enqueue
     */
    private void enqueue(final CallbackEventData callbackEventData) {
        try {
            callbackQueueService.submitToExecutor(callbackEventData);
        } catch (RejectedExecutionException e) {
            logger.info("CallbackEvent was rejected by the executor, saving to database: callbackEventId={}, {}", callbackEventData.id(), e.getMessage());
            logger.debug("CallbackEvent was rejected by the executor, saving to database: callbackEventId={}", callbackEventData.id(), e);
            callbackQueueService.enqueueToDatabase(callbackEventData);
        }
    }
}
