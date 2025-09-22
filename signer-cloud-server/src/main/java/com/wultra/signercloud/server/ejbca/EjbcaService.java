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
import com.wultra.signercloud.server.utils.CertificateUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;

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
     * @return the enrolled certificate with chain
     * @throws IOException if an error occurs during the enrollment process
     * @throws CertificateException if an error occurs during the enrollment process
     * @throws RestClientException if an error occurs during the enrollment process
     */
    public CertificateResponse enrollCertificate(final CertificateRequest request) throws IOException, CertificateException, RestClientException {
        final EjbcaRestClient.CertificateResponse certificateResponse = ejbcaRestClient.callPkcs10Enroll(convert(request));
        logger.info("Got certificate with serial number: {}", certificateResponse.serialNumber());
        if (!"DER".equals(certificateResponse.responseFormat())) {
            throw new IllegalStateException("Unexpected response format: " + certificateResponse.responseFormat());
        }

        final var x509Certificate = CertificateUtils.base64ToX509Certificate(certificateResponse.certificate());

        return new CertificateResponse(x509Certificate, certificateResponse.certificateChain());
    }

    private EjbcaRestClient.CertificateRequest convert(final CertificateRequest source) {
        return EjbcaRestClient.CertificateRequest.builder()
                .accountBindingId(source.userId())
                .username(source.externalSignerId())
                .certificateRequest(source.csr())
                .certificateProfileName(configurationProperties.getCertificateProfileName())
                .certificateAuthorityName(configurationProperties.getCertificateAuthorityName())
                .endEntityProfileName(configurationProperties.getEndEntityProfileName())
                .includeChain(true)
                .build();
    }

    /**
     * Revokes a specific certificate identified by its serial number and issuer DN.
     *
     * @param request the request containing the serial number and issuer DN of the certificate to be revoked
     * @throws RestClientException if an error occurs during the revocation process
     */
    public void revokeCertificate(final RevokeCertificateRequest request) throws RestClientException {
        ejbcaRestClient.revokeCertificate(request);
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

    /**
     * Enrolled certificate with chain.
     *
     * @param certificate enrolled end certificate
     * @param chain certificate chain, without the end certificate.
     */
    @Builder
    public record CertificateResponse(X509Certificate certificate, List<String> chain){}

    /**
     * Parameter object for revoking a certificate.
     *
     * @param serialNumber certificate serial number
     * @param issuerDN certificate issuer distinguished name
     */
    @Builder
    public record RevokeCertificateRequest(String serialNumber, String issuerDN) {}
}
