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

import com.github.benmanes.caffeine.cache.LoadingCache;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Handlers of a Callback Event response.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Component
@AllArgsConstructor
@Slf4j
class CallbackEventResponseHandler {

    private final CallbackConfigurationProperties configuration;

    private final CallbackEventRepository callbackEventRepository;

    private final LoadingCache<Long, CachedRestClient> callbackRestClientCache;

    /**
     * Handle a successful Callback Event attempt.
     *
     * @param callbackEventData Callback Event successfully delivered.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleSuccess(final CallbackEventData callbackEventData) {
        final CallbackEvent callbackEvent = callbackEventRepository.findById(callbackEventData.id())
                .orElseThrow(() -> new IllegalStateException("Callback Event was not found in database during its success handling: callbackEventId=" + callbackEventData.id()));

        logger.info("Callback succeeded, URL={}, callbackEventId={}", callbackEventData.config().url(), callbackEvent.getId());

        final Duration retentionPeriod = Objects.requireNonNullElse(callbackEventData.config().retentionPeriod(), configuration.getDefaultRetentionPeriod());

        callbackEventRepository.save(callbackEvent.toBuilder()
                .timestampDeleteAfter(LocalDateTime.now().plus(retentionPeriod))
                .timestampNextCall(null)
                .timestampRerunAfter(null)
                .attempts(callbackEvent.getAttempts() + 1)
                .status(CallbackEventStatus.COMPLETED)
                .build());
        resetFailureCount(callbackEvent.getCallbackId());
    }

    /**
     * Handle failure of callback attempt.
     *
     * @param callbackEventData Failed Callback Event.
     * @param error Exception describing the cause of failure.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleFailure(final CallbackEventData callbackEventData, final Throwable error) {
        final CallbackEvent callbackEvent = callbackEventRepository.findById(callbackEventData.id())
                .orElseThrow(() -> new IllegalStateException("Callback Event was not found in database during its failure handling: callbackEventId=" + callbackEventData.id()));

        logger.info("Callback failed, URL={}, callbackEventId={}, error={}", callbackEventData.config().url(), callbackEvent.getId(), error.getMessage());

        final CallbackEvent.CallbackEventBuilder builder = callbackEvent.toBuilder();
        builder.attempts(callbackEvent.getAttempts() + 1)
                .timestampRerunAfter(null);

        final int maxAttempts = Objects.requireNonNullElse(callbackEventData.config().maxAttempts(), configuration.getDefaultMaxAttempts());
        final int attemptsMade = callbackEvent.getAttempts();

        if (attemptsMade < maxAttempts) {
            final Duration initialBackoff = Objects.requireNonNullElse(callbackEventData.config().initialBackoff(), configuration.getDefaultInitialBackoff());
            final Duration backoffPeriod = calculateExponentialBackoffPeriod(callbackEvent.getAttempts(), initialBackoff, configuration.getBackoffMultiplier(), configuration.getMaxBackoff());
            builder.timestampNextCall(LocalDateTime.now().plus(backoffPeriod))
                    .status(CallbackEventStatus.PENDING);
        } else {
            logger.debug("Maximum number of attempts reached for callbackEventId={}", callbackEvent.getId());
            final Duration retentionPeriod = Objects.requireNonNullElse(callbackEventData.config().retentionPeriod(), configuration.getDefaultRetentionPeriod());
            builder.timestampDeleteAfter(LocalDateTime.now().plus(retentionPeriod))
                    .timestampNextCall(null)
                    .status(CallbackEventStatus.FAILED);
        }

        callbackEventRepository.save(builder.build());
        incrementFailureCount(callbackEvent.getId());
    }

    /**
     * Calculate the back off period for next retry attempt using exponential backoff strategy.
     *
     * @param attempts Number of already made attempts.
     * @param initialBackoff Initial backoff.
     * @return Duration between last and next attempt.
     */
    private static Duration calculateExponentialBackoffPeriod(final int attempts, final Duration initialBackoff, final double multiplier, final Duration maxBackoff) {
        Assert.isTrue(attempts >= 0, "Attempts must be non-negative.");
        Assert.isTrue(!initialBackoff.isNegative(), "Initial backoff must be non-negative.");

        if (attempts == 0) {
            return Duration.ZERO;
        }

        final long backoffMillis = (long) (initialBackoff.toMillis() * Math.pow(multiplier, attempts - 1));
        return Duration.ofMillis(Math.min(backoffMillis, maxBackoff.toMillis()));
    }

    private void incrementFailureCount(final Long callbackId) {
        if (configuration.failureStatsDisabled()) {
            return;
        }

        callbackRestClientCache.asMap().computeIfPresent(callbackId,
                (key, cached) -> CachedRestClient.builder()
                        .restClient(cached.restClient())
                        .timestampCreated(cached.timestampCreated())
                        .failureCount(cached.failureCount() + 1)
                        .timestampLastFailure(LocalDateTime.now())
                        .callback(cached.callback())
                        .build()
        );
    }

    private void resetFailureCount(final Long callbackId) {
        if (configuration.failureStatsDisabled()) {
            return;
        }

        callbackRestClientCache.asMap().computeIfPresent(callbackId,
                (key, cached) -> CachedRestClient.builder()
                        .restClient(cached.restClient())
                        .timestampCreated(cached.timestampCreated())
                        .failureCount(0)
                        .timestampLastFailure(cached.timestampLastFailure())
                        .callback(cached.callback())
                        .build()
        );
    }
}
