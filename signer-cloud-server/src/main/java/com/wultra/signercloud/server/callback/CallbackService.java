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
import com.wultra.core.rest.client.base.RestClientException;
import com.wultra.signercloud.server.utils.TransactionUtils;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Callback service.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Service
@AllArgsConstructor
@Slf4j
class CallbackService {

    private final CallbackConfigurationProperties configuration;

    private final CallbackEventResponseHandler callbackEventResponseHandler;

    private final CallbackEventRepository callbackEventRepository;

    private final CallbackConvertor callbackConvertor;

    private final CallbackRestClient expiredCallbackClient;

    private final CallbackRestClient renewedCallbackClient;

    private final CallbackConfigurationProperties callbackConfigurationProperties;

    /**
     * Dispatch a Callback Event.
     *
     * @param callbackEventData Callback Event to dispatch.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public void dispatchInstantCallbackEvent(final CallbackEventData callbackEventData) {
        postCallback(callbackEventData);
    }

    /**
     * Move a Callback Event to the PENDING state.
     *
     * @param callbackEventData Callback Event to set as PENDING.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void moveCallbackEventToPending(final CallbackEventData callbackEventData) {
        callbackEventRepository.updateEventToPendingState(callbackEventData.id());
    }

    /**
     * Create and save a new {@link CallbackEvent} in processing state.
     *
     * @param callbackType Callback callbackType.
     * @param callbackData Data to be sent with the Callback URL.
     * @return Saved {@link CallbackEvent}.
     */
    @Transactional
    public CallbackEvent createAndSaveEventForProcessing(final CallbackType callbackType, final String callbackData) {
        final LocalDateTime now = LocalDateTime.now();
        final Duration forceRerunPeriod = Objects.requireNonNullElse(configuration.getForceRerunPeriod(), defaultForceRerunPeriod());

        final CallbackEvent callbackEvent = CallbackEvent.builder()
                .status(CallbackEventStatus.PROCESSING)
                .callbackData(callbackData)
                .callbackType(callbackType)
                .timestampCreated(now)
                .timestampLastCall(now)
                .timestampRerunAfter(shouldBeSentAtMostOnce(configuration.callbackConfigurationFor(callbackType)) ? null : now.plus(forceRerunPeriod))
                .attempts(0)
                .idempotencyKey(UUID.randomUUID().toString())
                .build();

        return callbackEventRepository.save(callbackEvent);
    }

    /**
     * Dispatch Callback Events in pending state.
     */
    @Transactional
    public int dispatchPendingCallbackEvents(final int limit) {
        final List<CallbackEvent> callbackEvents = callbackEventRepository.findPending(LocalDateTime.now(), limit);
        callbackEvents
                .forEach(this::dispatchPendingCallbackEvent);
        return callbackEvents.size();
    }

    /**
     * Delete Callback Events that are past their retention period.
     */
    @Transactional
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
    @Transactional
    public int resetStaleCallbackEvents() {
        final int numberOfAffectedEvents = callbackEventRepository.updateStaleEventsToPendingState(LocalDateTime.now());
        logger.info("Number of stale Callback Events moved to PENDING state: {}", numberOfAffectedEvents);
        return numberOfAffectedEvents;
    }

    private CallbackRestClient fetchCallbackRestClient(final CallbackType callbackType) {
        return switch (callbackType) {
            case EXPIRED -> expiredCallbackClient;
            case RENEWED -> renewedCallbackClient;
        };
    }

    /**
     * Dispatch Callback Event.
     * @param callbackEvent Event to dispatch.
     */
    private void dispatchPendingCallbackEvent(final CallbackEvent callbackEvent) {
        final CallbackType callbackType = callbackEvent.getCallbackType();

        final CallbackRestClient callbackRestClient = fetchCallbackRestClient(callbackType);

        if (failureThresholdReached(callbackRestClient)) {
            logger.warn("Callback has reached failure threshold, associated events are not dispatched: callbackType={}", callbackType);
            failWithoutDispatching(callbackEvent);
            return;
        }

        final LocalDateTime timestampNow = LocalDateTime.now();
        final Duration forceRerunPeriod = Objects.requireNonNullElse(configuration.getForceRerunPeriod(), defaultForceRerunPeriod());

        final CallbackEvent savedCallbackEvent = callbackEventRepository.save(callbackEvent.toBuilder()
                .status(CallbackEventStatus.PROCESSING)
                .timestampNextCall(null)
                .timestampLastCall(timestampNow)
                .timestampRerunAfter(shouldBeSentAtMostOnce(configuration.callbackConfigurationFor(callbackType)) ? null : timestampNow.plus(forceRerunPeriod))
                .build());

        final CallbackEventData callbackEventData = callbackConvertor.convert(savedCallbackEvent);

        TransactionUtils.executeAfterTransactionCommits(() -> postCallback(callbackEventData));
    }

    /**
     * Check if the Callback should be processed. This check prevents from failed callback event flooding.
     *
     * @param callbackType Callback type.
     * @return True if the callback should be processed, false otherwise.
     */
    public boolean failureThresholdReached(final CallbackType callbackType) {
        return failureThresholdReached(fetchCallbackRestClient(callbackType));
    }

    boolean failureThresholdReached(final CallbackRestClient callbackRestClient) {
        if (configuration.failureStatsDisabled()) {
            logger.debug("Failure stats are turned off for Callback processing");
            return false;
        }

        final int failureThreshold = configuration.getFailureThreshold();
        final Duration resetTimeout = configuration.getFailureResetTimeout();

        final int failureCount = callbackRestClient.failureCount().get();
        final LocalDateTime timestampLastFailure = callbackRestClient.timestampLastFailure().get();

        if (failureCount >= failureThreshold && LocalDateTime.now().minus(resetTimeout).isAfter(timestampLastFailure)) {
            logger.debug("Callback reached failure threshold, but before specified reset timeout period, callbackType={}", callbackRestClient.callbackType());
            return false;
        }

        return failureCount >= failureThreshold;
    }

    /**
     * Create and save a new {@link CallbackEvent} in failed state.
     *
     * @param callbackType Callback type.
     * @param callbackData Data to be sent with the Callback URL.
     */
    public void createAndSaveFailedEvent(final CallbackType callbackType, final String callbackData) {
        final CallbackEvent callbackEvent = CallbackEvent.builder()
                .callbackType(callbackType)
                .callbackData(callbackData)
                .idempotencyKey(UUID.randomUUID().toString())
                .attempts(0)
                .timestampCreated(LocalDateTime.now())
                .build();
        callbackEventRepository.save(failWithoutDispatching(callbackEvent));
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

    private boolean shouldBeSentAtMostOnce(final CallbackConfigurationProperties.CallbackConfiguration configuration) {
        return configuration.maxAttempts() == 1;
    }

    /**
     * Send Callback Event as a non-blocking POST request.
     *
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

            final RestClient restClient = fetchCallbackRestClient(callbackEventData.callbackType()).restClient();
            final MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
            headers.add("Idempotency-Key", callbackEventData.idempotencyKey());

            // not needed to specify the path, using restClient baseUrl
            restClient.postNonBlocking("",
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

    private CallbackEvent failWithoutDispatching(final CallbackEvent callbackEvent) {
        final Duration retentionPeriod = callbackConfigurationProperties.callbackConfigurationFor(callbackEvent.getCallbackType()).retentionPeriod();

        return callbackEventRepository.save(callbackEvent.toBuilder()
                .status(CallbackEventStatus.FAILED)
                .timestampNextCall(null)
                .timestampDeleteAfter(LocalDateTime.now().plus(retentionPeriod))
                .timestampRerunAfter(null)
                .build());
    }
}
