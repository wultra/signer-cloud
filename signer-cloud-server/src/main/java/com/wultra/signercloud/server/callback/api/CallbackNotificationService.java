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
package com.wultra.signercloud.server.callback.api;

/**
 * Callback notification service.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
public interface CallbackNotificationService {

    /**
     * Notify the system about a new Callback Event. The event is enqueued <strong>after the current transaction commits</strong>.
     * <p>
     * The event is created and saved to the database, then it is submitted to a task executor for processing.
     * If the executor rejects the event, it remains in the database with PENDING status and will be processed later by a scheduled job.
     * <p>
     * If the failure threshold for the given Callback Type has been reached, no event is created and saved, instead a failed event is created.
     *
     * @param callbackType Type of the callback.
     * @param callbackData Data associated with the callback will be stored as JSON.
     */
    void notify(CallbackType callbackType, String callbackData);

    /**
     * Check if the callback is enabled.
     * @param callbackType Type of the callback.
     * @return True if the callback is enabled, false otherwise.
     */
    boolean isCallbackEnabled(CallbackType callbackType);
}
