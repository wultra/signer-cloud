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
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.wultra.core.rest.client.base.RestClient;
import com.wultra.core.rest.client.base.RestClientException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Service
@Transactional
@AllArgsConstructor
@Slf4j
public class CallbackService {

    private final CallbackRepository callbackRepository;

    private final CallbackConfigurationProperties configuration;

    private final CallbackEventResponseHandler callbackEventResponseHandler;

    private final CallbackEventRepository callbackEventRepository;

    private final LoadingCache<Long, CachedRestClient> restClientCache;

    private final ObjectMapper objectMapper;

    /**
     * Saves a new {@link CallbackEvent}s to the repository.
     *
     * @param events the events to save
     */
    public void save(final Iterable<CallbackEvent> events) {
        callbackEventRepository.saveAll(events);
    }

    /**
     * Finds a list of {@link Callback} entities of the specified type.
     *
     * @param callbackType the type of callback to filter by
     * @return a list of matching {@link Callback} entities, or an empty list if none are found
     */
    public List<Callback> findCallbacks(final CallbackType callbackType) {
        return callbackRepository.findCallbacksByCallbackType(callbackType);
    }

    /**
     * Dispatch Callback Events in pending state.
     */
    public int dispatchPendingCallbackEvents(final int limit) {
        final List<CallbackEvent> callbackEvents = callbackEventRepository.findPending(LocalDateTime.now(), limit);
        callbackEvents
                .forEach(this::dispatchPendingCallbackEvent);
        return callbackEvents.size();
    }

    /**
     * Delete Callback Events that are past their retention period.
     */
    public int deleteCallbackEventsAfterRetentionPeriod() {
        return callbackEventRepository.deleteCompletedAfterRetentionPeriod(LocalDateTime.now());
    }

    /**
     * Reset stale Callback Events in the PROCESSING state by setting them to the PENDING state.
     * <p>
     * This should be applied only to those Callback Events, that got stuck in the PROCESSING state
     * and won't be dispatched without this action. Otherwise, there is a risk of posting
     * a Callback Event more than once.
     */
    public int resetStaleCallbackEvents() {
        final int numberOfAffectedEvents = callbackEventRepository.updateStaleEventsToPendingState(LocalDateTime.now());
        logger.info("Number of stale Callback Events moved to PENDING state: {}", numberOfAffectedEvents);
        return numberOfAffectedEvents;
    }

    /**
     * Dispatch Callback Event.
     * @param callbackEvent Event to dispatch.
     */
    private void dispatchPendingCallbackEvent(final CallbackEvent callbackEvent) {
        final CachedRestClient restClient = restClientCache.get(callbackEvent.getCallbackId());

        if (restClient == null) {
            logger.warn("Callback is not available, associated events are not dispatched: callbackId={}", callbackEvent.getCallbackId());
            failWithoutDispatching(callbackEvent, null);
            return;
        }

        final Callback callback = restClient.callback();
        if (failureThresholdReached(callback)) {
            logger.warn("Callback has reached failure threshold, associated events are not dispatched: callbackId={}", callback.getId());
            failWithoutDispatching(callbackEvent, callback);
            return;
        }

        final LocalDateTime timestampNow = LocalDateTime.now();
        final Duration forceRerunPeriod = Objects.requireNonNullElse(configuration.getForceRerunPeriod(), defaultForceRerunPeriod());

        final CallbackEvent savedCallbackEvent = callbackEventRepository.save(callbackEvent.toBuilder()
                .status(CallbackEventStatus.PROCESSING)
                .timestampNextCall(null)
                .timestampLastCall(timestampNow)
                .timestampRerunAfter(shouldBeSentAtMostOnce(callback) ? null : timestampNow.plus(forceRerunPeriod))
                .build());

        final CallbackEventData callbackEventData = convert(savedCallbackEvent, callback);

        executeAfterTransactionCommits(() -> postCallback(callbackEventData));
    }

    /**
     * Check if the Callback should be processed. This check prevents from failed callback event flooding.
     *
     * @param callback Callback entity holding failure statistics.
     * @return True if the callback should be processed, false otherwise.
     */
    private boolean failureThresholdReached(final Callback callback) {
        if (configuration.failureStatsDisabled()) {
            logger.debug("Failure stats are turned off for Callback processing");
            return false;
        }

        final Long callbackId = callback.getId();
        final CachedRestClient restClient = restClientCache.getIfPresent(callbackId);
        if (restClient == null) {
            logger.debug("No failure stats available yet for Callback processing: id={}", callbackId);
            return false;
        }

        final int failureThreshold = configuration.getFailureThreshold();
        final Duration resetTimeout = configuration.getFailureResetTimeout();

        final int failureCount = restClient.failureCount();
        final LocalDateTime timestampLastFailure = restClient.timestampLastFailure();

        if (failureCount >= failureThreshold && LocalDateTime.now().minus(resetTimeout).isAfter(timestampLastFailure)) {
            logger.debug("Callback reached failure threshold, but before specified reset timeout period, id={}", callbackId);
            return false;
        }

        return failureCount >= failureThreshold;
    }

    /**
     * Get the default force rerun period, after which is a Callback Event in PROCESSING state considered stale.
     * @return Default force rerun period.
     */
    private Duration defaultForceRerunPeriod() {
        // This is an arbitrary value, representing allowed delay before trying establishing remote connection.
        final Duration allowedProcessingDelay = Duration.ofSeconds(10);
        return configuration.getHttpConnectionTimeout()
                .plus(configuration.getHttpResponseTimeout())
                .plus(allowedProcessingDelay);
    }

    private boolean shouldBeSentAtMostOnce(final Callback callback) {
        return obtainMaxAttempts(callback) == 1;
    }

    /**
     * Obtain maximum attempts to send a Callback Event.
     * @param callback The Callback Event configuration.
     * @return Maximum number of attempts.
     */
    public int obtainMaxAttempts(final Callback callback) {
        return Objects.requireNonNullElse(callback.getMaxAttempts(), configuration.getDefaultMaxAttempts());
    }

    /**
     * Send Callback Event as a non-blocking POST request.
     * @param callbackEventData Event to post.
     */
    private void postCallback(final CallbackEventData callbackEventData) {
        if (callbackEventData.status() != CallbackEventStatus.PROCESSING) {
            logger.warn("Callback Event to post is not in PROCESSING state: callbackEventId={}", callbackEventData.id());
            return;
        }

        try {
            final Consumer<ResponseEntity<String>> onSuccess = response -> callbackEventResponseHandler.handleSuccess(callbackEventData);
            final Consumer<Throwable> onError = error -> callbackEventResponseHandler.handleFailure(callbackEventData, error);
            final ParameterizedTypeReference<String> responseType = new ParameterizedTypeReference<>(){};

            final RestClient restClient = fetchRestClient(callbackEventData);
            final MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
            headers.add("Idempotency-Key", callbackEventData.idempotencyKey());

            restClient.postNonBlocking(callbackEventData.config().url(),
                    callbackEventData.callbackData(),
                    new LinkedMultiValueMap<>(),
                    headers,
                    responseType,
                    onSuccess,
                    onError);

            logger.debug("CallbackEvent {} was dispatched.", callbackEventData.id());
        } catch (RestClientException e) {
            callbackEventResponseHandler.handleFailure(callbackEventData, e);
        }
    }

    private RestClient fetchRestClient(final CallbackEventData callbackEventData) throws RestClientException {
        final Long callbackUrlId = callbackEventData.config().id();
        final CachedRestClient restClient = restClientCache.get(callbackUrlId);
        if (restClient == null) {
            throw new RestClientException("REST Client not available for the Callback: id=" + callbackUrlId);
        }

        return restClient.restClient();
    }

    private void failWithoutDispatching(final CallbackEvent callbackEvent, final Callback callback) {
        final Duration retentionPeriod = resolveRetentionPeriod(callback);

        callbackEventRepository.save(callbackEvent.toBuilder()
                .status(CallbackEventStatus.FAILED)
                .timestampNextCall(null)
                .timestampDeleteAfter(LocalDateTime.now().plus(retentionPeriod))
                .timestampRerunAfter(null)
                .build());
    }

    private Duration resolveRetentionPeriod(final Callback callback) {
        if (callback != null && callback.getRetentionPeriod() != null) {
            return callback.getRetentionPeriod();
        } else {
            return configuration.getDefaultRetentionPeriod();
        }
    }

    private CallbackEventData convert(final CallbackEvent callbackEvent, final Callback callback) {
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

    /**
     * Execute a task after the current transaction commits.
     * @param task Task to execute.
     */
    private static void executeAfterTransactionCommits(final Runnable task) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }
}
