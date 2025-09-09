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
