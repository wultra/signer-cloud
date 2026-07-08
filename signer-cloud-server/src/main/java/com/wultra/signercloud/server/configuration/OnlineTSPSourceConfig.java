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
package com.wultra.signercloud.server.configuration;

import eu.europa.esig.dss.service.http.commons.TimestampDataLoader;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * Configuration for TSA source.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Configuration
@AllArgsConstructor
@Slf4j
class OnlineTSPSourceConfig {

    private final PAdESConfigurationProperties pAdESConfigurationProperties;

    @Bean
    @ConditionalOnProperty("signer-cloud.server.pades.tsa-url")
    public OnlineTSPSource tspSource() {
        final var tsaUrl = pAdESConfigurationProperties.getTsaUrl();
        logger.info("Setting TSA URL", kv("tsaUrl", tsaUrl));

        final var onlineTspSource = new OnlineTSPSource(tsaUrl);
        onlineTspSource.setDataLoader(new TimestampDataLoader());

        return onlineTspSource;
    }
}
