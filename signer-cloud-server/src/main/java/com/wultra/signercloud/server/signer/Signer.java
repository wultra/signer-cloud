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
package com.wultra.signercloud.server.signer;

import com.wultra.signercloud.server.utils.CertificateUtils;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Sequence;
import org.springframework.data.relational.core.mapping.Table;

import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;

/**
 * Data Access Object for the {@code sc_signer} table.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Getter
@Builder(toBuilder = true)
@Table("sc_signer")
@Slf4j
public class Signer {

    private static final String CERTIFICATES_SEPARATOR = ",";

    @Id
    @Sequence("sc_signer_seq")
    private long id;

    private Instant timestampCreated;

    private Instant timestampLastUpdated;

    private String externalSignerId;

    private String userId;

    private String csr;

    /**
     * Certificate encoded in Base64.
     */
    private String certificate;

    private Instant timestampCertificateExpiration;

    private SignerStatus status;

    private String certificateChain;

    /**
     * Returns {@link #getCertificate()} as {@link X509Certificate}.
     *
     * @return certificate
     * @throws CertificateException in case of an error during certificate parsing.
     */
    public X509Certificate getX509Certificate() throws CertificateException {
        return CertificateUtils.base64ToX509Certificate(certificate);
    }

    public List<String> getCertificateChain() {
        return Optional.ofNullable(certificateChain)
                .map(i -> i.split(CERTIFICATES_SEPARATOR))
                .map(Arrays::asList)
                .orElse(Collections.emptyList());
    }

    public static class SignerBuilder {

        /**
         * Set {@code certificate} from {@link X509Certificate}.
         *
         * @param x509Certificate X509Certificate to set
         * @return builder instance
         */
        public SignerBuilder certificateFromX509(final X509Certificate x509Certificate) throws CertificateEncodingException {
            this.certificate = Base64.getEncoder().encodeToString(x509Certificate.getEncoded());
            return this;
        }

        /**
         * Sets the {@link Signer#certificateChain} from a {@code List<String>}, where each item is a Base64-encoded certificate in DER format.
         *
         * @param certificateChain chain to set
         * @return builder instance
         */
        public SignerBuilder certificateChainFromList(final List<String> certificateChain) {
            this.certificateChain = String.join(CERTIFICATES_SEPARATOR, certificateChain);
            return this;
        }
    }
}
