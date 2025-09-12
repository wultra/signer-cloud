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

import com.wultra.core.rest.client.base.RestClient;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Wrapper for the {@link RestClient} to track the creation timestamp and failure statistics of a REST Client instance.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Builder
record CallbackRestClient(
        RestClient restClient,
        LocalDateTime timestampCreated,
        AtomicInteger failureCount,
        AtomicReference<LocalDateTime> timestampLastFailure,
        CallbackType callbackType
) { }
