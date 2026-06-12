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

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Callback converter.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Component
@AllArgsConstructor
class CallbackConvertor {

    private final ObjectMapper objectMapper;

    public CallbackEventData convert(final CallbackEvent callbackEvent) {
        return CallbackEventData.builder()
                .id(callbackEvent.getId())
                .callbackType(callbackEvent.getCallbackType())
                .callbackData(convert(callbackEvent.getCallbackData()))
                .status(callbackEvent.getStatus())
                .idempotencyKey(callbackEvent.getIdempotencyKey())
                .build();
    }

    private Map<String, Object> convert(String source) {
        if (source == null) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(source, new TypeReference<>() {});
        } catch (JacksonException ex) {
            throw new IllegalStateException("Unable to parse JSON payload", ex);
        }
    }
}
