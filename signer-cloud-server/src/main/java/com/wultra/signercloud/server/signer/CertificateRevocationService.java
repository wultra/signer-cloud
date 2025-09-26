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

import com.wultra.core.rest.client.base.RestClientException;
import com.wultra.signercloud.server.ejbca.EjbcaService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.Instant;

/**
 * Service for revoking issued certificates in EJBCA.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Service
@AllArgsConstructor
@Slf4j
class CertificateRevocationService {

    private final IssuedCertificateMetadataRepository issuedCertificateMetadataRepository;
    private final EjbcaService ejbcaService;

    /**
     * Revokes single certificate in EJBCA and updates {@link IssuedCertificateMetadata#status} to {@link IssuedCertificateStatus#REVOKED}.
     * This method always runs in a new transaction.
     *
     * @param certificateMetadata Certificate metadata to be revoked.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void revokeCertificate(final IssuedCertificateMetadata certificateMetadata, final RevocationReason revocationReason) {
        final var issuerDn = certificateMetadata.getIssuerDn();
        final var serialNumber = certificateMetadata.getSerialNumber();

        try {
            final var serialNumberHex = new BigInteger(serialNumber).toString(16);

            final var request = new EjbcaService.RevokeCertificateRequest(
                    serialNumberHex,
                    issuerDn,
                    revocationReason
            );

            ejbcaService.revokeCertificate(request);

            setRevokedStatus(certificateMetadata);
            logger.info("Certificate successfully revoked. Issuer DN: {}, Serial Number: {}", issuerDn, serialNumber);
        } catch (final RestClientException e) {
            logger.warn("Error from EJBCA server when revoking certificate: {}", e.getResponse(), e);

            if (e.getStatusCode() == HttpStatus.CONFLICT && e.getResponse().contains("has previously been revoked")) {
                logger.info("Certificate was already revoked in EJBCA. Issuer DN: {}, Serial Number: {}", issuerDn, serialNumber);
                setRevokedStatus(certificateMetadata);
                return;
            }

            throw new CertificateAuthorityException("Error from EJBCA server when revoking certificate: " + e.getResponse(), e);
        }
    }

    private void setRevokedStatus(final IssuedCertificateMetadata certificateMetadata) {
        final var updatedCertificateMetadata = certificateMetadata.toBuilder()
                .timestampLastUpdated(Instant.now())
                .status(IssuedCertificateStatus.REVOKED)
                .build();

        issuedCertificateMetadataRepository.save(updatedCertificateMetadata);
    }
}
