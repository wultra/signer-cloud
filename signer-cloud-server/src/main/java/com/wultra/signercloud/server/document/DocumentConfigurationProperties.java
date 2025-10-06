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
package com.wultra.signercloud.server.document;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Document configuration properties.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@ConfigurationProperties(prefix = "signer-cloud.server.document")
@Getter
@Setter
class DocumentConfigurationProperties {

    private DocumentConfiguration waiting = new DocumentConfiguration();

    private DocumentConfiguration rejected = new DocumentConfiguration();

    private DocumentConfiguration signed = new DocumentConfiguration();

    /**
     * Algorithm used to compute the hash of the document for signing.
     */
    private DigestAlgorithm hashAlgorithm;

    @Getter
    @Setter
    static class DocumentConfiguration {

        /**
         * Retention period for documents. Empty value means no retention period is used, value 0 means documents will be deleted immediately.
         */
        private Duration retentionPeriod;

        /**
         * Maximum timeout threshold for signing the document after upload. It is applicable only for {@link DocumentConfigurationProperties#waiting}.
         */
        private Duration timeout;
    }

}
