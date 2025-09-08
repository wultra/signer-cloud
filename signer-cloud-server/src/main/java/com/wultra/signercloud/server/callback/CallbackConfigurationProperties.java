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

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

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

    private Configuration dispatchPendingCallbackEvents = new Configuration(new Job(100));

    /**
     * Default maximum number of attempts in case the corresponding Callback does not define any.
     */
    private int defaultMaxAttempts = 1;

    /**
     * Default retention period of the event in case the corresponding Callback does not define any.
     */
    private Duration defaultRetentionPeriod = Duration.ofDays(30);

    /**
     * Default initial backoff between attempts in case the corresponding Callback does not define any.
     */
    private Duration defaultInitialBackoff = Duration.ofSeconds(2);

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

    boolean failureStatsDisabled() {
        return failureThreshold == -1;
    }

    record Configuration(Job job) {
    }

    record Job(int limit) {
    }
}
