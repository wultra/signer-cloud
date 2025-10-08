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

import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.service.http.commons.TimestampDataLoader;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for {@link PAdESService}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Configuration
@AllArgsConstructor
@Slf4j
public class PAdESServiceConfig {

    private final PAdESConfigurationProperties pAdESConfigurationProperties;

    @Bean
    public PAdESService padesService() {
        final var tsaUrl = pAdESConfigurationProperties.getTsaUrl();

        final var padesService = new PAdESService(new CommonCertificateVerifier());

        if (StringUtils.isNotEmpty(tsaUrl)) {
            final var tspSource = new OnlineTSPSource(tsaUrl);
            tspSource.setDataLoader(new TimestampDataLoader());

            padesService.setTspSource(tspSource);
            logger.info("Set TSA URL: {}", tsaUrl);
        }

        return padesService;
    }

}
