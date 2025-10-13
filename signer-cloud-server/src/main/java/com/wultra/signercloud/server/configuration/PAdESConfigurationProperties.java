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

import com.wultra.signercloud.server.document.DocumentSignatureLevel;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.pades.signature.PAdESService;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for {@link PAdESService}
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@ConfigurationProperties(prefix = "signer-cloud.server.pades")
@Getter
@Setter
public class PAdESConfigurationProperties {

    /**
     * URL of TSA endpoint providing timestamp for {@link DocumentSignatureLevel#PADES_B_T} according to RFC 3161.
     */
    private String tsaUrl;

    /**
     * Default signature for the document.
     */
    private DocumentSignatureLevel signatureLevel;

    /**
     * Algorithm used to compute the hash of the document for signing.
     */
    private DigestAlgorithm hashAlgorithm;

    /**
     * Algorithm use for signing the document. The {@link DigestAlgorithm} must match {@link #hashAlgorithm}.
     */
    private SignatureAlgorithm signatureAlgorithm;
}
