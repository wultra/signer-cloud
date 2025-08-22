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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Service
@Transactional
@AllArgsConstructor
public class CallbackService {

    private final CallbackEventRepository callbackEventRepository;

    /**
     * Saves a new {@link CallbackEvent}s to the repository.
     *
     * @param events the events to save
     */
    public void save(final Iterable<CallbackEvent> events) {
        callbackEventRepository.saveAll(events);
    }
}
