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

import com.wultra.signercloud.server.callback.api.CallbackType;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

import java.time.Duration;

/**
 * Callback configuration properties.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@ConfigurationProperties(prefix = "signer-cloud.server.callback")
@Getter
@Setter
class CallbackConfigurationProperties {

    private JobConfiguration dispatchPendingCallbackEvents = new JobConfiguration(new Job(100));

    /**
     * Configuration for {@link CallbackType#EXPIRED}.
     */
    private CallbackConfiguration expired;

    /**
     * Configuration for {@link CallbackType#RENEWED}.
     */
    private CallbackConfiguration renewed;

    /**
     * Maximum possible backoff period between successive attempts.
     */
    private Duration maxBackoff = Duration.ofSeconds(32);

    /**
     * Multiplier used to calculate the backoff period.
     */
    private double backoffMultiplier = 1.5;

    /**
     * Period after which a Callback Event is considered stale and should be dispatched again.
     * The default value is computed as a function of configured HTTP timeouts.
     */
    private Duration forceRerunPeriod;

    /**
     * Number of allowed Callback Events failures in a row.
     * When the threshold is reached, no other events with the same Callback configuration will be posted.
     * {@code -1} means that the threshold is disabled.
     */
    private int failureThreshold = 200;

    /**
     * Period after which a Callback Event will be dispatched even though the failure threshold is reached.
     */
    private Duration failureResetTimeout = Duration.ofSeconds(60);

    /**
     * Whether HTTP proxy is enabled for outgoing HTTP requests.
     */
    private Boolean httpProxyEnabled;

    /**
     * HTTP proxy host.
     */
    private String httpProxyHost;

    /**
     * HTTP proxy port.
     */
    private Integer httpProxyPort;

    /**
     * HTTP proxy username, use only in case HTTP proxy authentication is required.
     */
    private String httpProxyUsername;

    /**
     * HTTP proxy password, use only in case HTTP proxy authentication is required.
     */
    private String httpProxyPassword;

    /**
     * HTTP connection timeout.
     */
    private Duration httpConnectionTimeout = Duration.ofSeconds(5);

    /**
     * HTTP response timeout.
     */
    private Duration httpResponseTimeout = Duration.ofSeconds(60);

    /**
     * HTTP connection max idle time.
     */
    private Duration httpMaxIdleTime;

    /**
     * Number of core threads in the thread pool.
     */
    private int threadPoolCoreSize = 1;

    /**
     * Maximum number of threads in the thread pool.
     */
    private int threadPoolMaxSize = 2;

    /**
     * Queue capacity of the thread pool.
     */
    private int threadPoolQueueCapacity = 1000;

    @PostConstruct
    void validate() {
        Assert.isTrue(!expired.enabled() || StringUtils.isNotBlank(expired.url()), "If expired callback is enabled, URL must not be blank.");
        Assert.isTrue(!renewed.enabled() || StringUtils.isNotBlank(renewed.url()), "If renewed callback is enabled, URL must not be blank.");
    }

    boolean failureStatsDisabled() {
        return failureThreshold == -1;
    }

    public CallbackConfiguration callbackConfigurationFor(final CallbackType callbackType) {
        return switch (callbackType) {
            case EXPIRED -> expired;
            case RENEWED -> renewed;
        };
    }

    record JobConfiguration(Job job) {
    }

    record Job(int limit) {
    }

    /**
     * Callback configuration.
     *
     * @param url
     * @param maxAttempts Maximum number of attempts to send the callback.
     * @param initialBackoff Initial backoff before the next sending attempt.
     * @param retentionPeriod Duration for which the callback event is stored.
     * @param authentication Callback authentication.
     * @param enabled Whether the callback is enabled.
     */
    record CallbackConfiguration(String url, Integer maxAttempts, Duration initialBackoff, Duration retentionPeriod, CallbackAuthentication authentication, boolean enabled) {
        @Override
        public String toString() {
            return "CallbackConfiguration{" +
                    "retentionPeriod=" + retentionPeriod +
                    ", initialBackoff=" + initialBackoff +
                    ", maxAttempts=" + maxAttempts +
                    ", enabled=" + enabled +
                    ", url='" + url + '\'' +
                    // authentication omitted on purpose
                    '}';
        }
    }
}
