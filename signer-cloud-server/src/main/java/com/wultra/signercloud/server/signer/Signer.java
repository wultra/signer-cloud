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

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Sequence;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.util.Assert;

import java.io.ByteArrayInputStream;
import java.security.cert.*;
import java.time.Instant;
import java.util.Base64;

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

    private static final String CERTIFICATE_TYPE = "X.509";

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

    /**
     * Returns {@link #getCertificate()} as {@link X509Certificate}.
     *
     * @return certificate
     * @throws CertificateException in case of an error during certificate parsing.
     */
    public X509Certificate getX509Certificate() throws CertificateException {
        final var certificateBytes = Base64.getDecoder().decode(certificate);
        final Certificate result = CertificateFactory.getInstance(CERTIFICATE_TYPE)
                .generateCertificate(new ByteArrayInputStream(certificateBytes));
        Assert.isInstanceOf(X509Certificate.class, result, "Certificate is must be of type X509Certificate");
        return (X509Certificate) result;
    }
}
