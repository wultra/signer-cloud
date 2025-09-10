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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * Callback converter.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Component
@AllArgsConstructor
public class CallbackConvertor {

    private final ObjectMapper objectMapper;

    public CallbackEventData convert(final CallbackEvent callbackEvent, final Callback callback) {
        return CallbackEventData.builder()
                .id(callbackEvent.getId())
                .callbackData(convert(callbackEvent.getCallbackData()))
                .status(callbackEvent.getStatus())
                .idempotencyKey(callbackEvent.getIdempotencyKey())
                .config(convert(callback))
                .build();
    }

    private Map<String, Object> convert(String source) {
        if (source == null) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(source, new TypeReference<>() {});
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to parse JSON payload", ex);
        }
    }

    private static CallbackData convert(final Callback callback) {
        return CallbackData.builder()
                .id(callback.getId())
                .url(callback.getCallbackUrl())
                .retentionPeriod(callback.getRetentionPeriod())
                .initialBackoff(callback.getInitialBackoff())
                .maxAttempts(callback.getMaxAttempts())
                .build();
    }
}
