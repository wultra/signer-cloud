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
package com.wultra.signercloud.server.ejbca;

import com.wultra.core.rest.client.base.RestClientException;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Service for calling EJBCA functionality.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Service
@AllArgsConstructor
@Slf4j
public class EjbcaService {

    private final EjbcaRestClient ejbcaRestClient;

    private final EjbcaConfigurationProperties configurationProperties;

    /**
     * Enrolls a certificate using the provided Certificate Signing Request (CSR).
     *
     * @param request the request containing CSR and other metadata
     * @return the enrolled certificate
     * @throws IOException if an error occurs during the enrollment process
     * @throws CertificateException if an error occurs during the enrollment process
     * @throws RestClientException if an error occurs during the enrollment process
     */
    public X509Certificate enrollCertificate(final CertificateRequest request) throws IOException, CertificateException, RestClientException {
        final EjbcaRestClient.CertificateResponse certificateResponse = ejbcaRestClient.callPkcs10Enroll(convert(request));
        logger.info("Got certificate with serial number: {}", certificateResponse.serialNumber());
        if (!"DER".equals(certificateResponse.responseFormat())) {
            throw new IllegalStateException("Unexpected response format: " + certificateResponse.responseFormat());
        }

        return convertDerToX509Certificate(certificateResponse.certificate());
    }

    private EjbcaRestClient.CertificateRequest convert(final CertificateRequest source) {
        return EjbcaRestClient.CertificateRequest.builder()
                .accountBindingId(source.userId())
                .username(source.externalSignerId())
                .certificateRequest(source.csr())
                .certificateProfileName(configurationProperties.getCertificateProfileName())
                .certificateAuthorityName(configurationProperties.getCertificateAuthorityName())
                .endEntityProfileName(configurationProperties.getEndEntityProfileName())
                .build();
    }

    private static X509Certificate convertDerToX509Certificate(final String derCertificateBase64) throws IOException, CertificateException {
        final byte[] derCertificate = Base64.getDecoder().decode(derCertificateBase64);
        final CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
        try (final ByteArrayInputStream inputStream = new ByteArrayInputStream(derCertificate)) {
            return (X509Certificate) certificateFactory.generateCertificate(inputStream);
        }
    }

    /**
     * Parameter object for enrolling a certificate.
     *
     * @param userId user identifier
     * @param externalSignerId signer identifier
     * @param csr Certificate Signing Request in PKCS #10 format
     */
    @Builder
    public record CertificateRequest(String userId, String externalSignerId, String csr){}
}
