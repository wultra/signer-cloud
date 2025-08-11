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
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMParser;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.StringReader;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * Service for calling EJBCA functionality.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Service
@AllArgsConstructor
public class EjbcaService {

    private final EjbcaRestClient ejbcaRestClient;

    private EjbcaConfigurationProperties configurationProperties;

    /**
     * Enrolls a certificate using the provided Certificate Signing Request (CSR).
     *
     * @param csr the Certificate Signing Request in PKCS #10 format
     * @return the enrolled certificate
     * @throws IOException if an error occurs during the enrollment process
     * @throws CertificateException if an error occurs during the enrollment process
     * @throws RestClientException if an error occurs during the enrollment process
     */
    public X509Certificate enrollCertificate(final String csr) throws IOException, CertificateException, RestClientException {
        final CertificateRequest request = CertificateRequest.builder()
                .certificateRequest(csr)
                .certificateProfileName(configurationProperties.getCertificateProfileName())
                .certificateAuthorityName(configurationProperties.getCertificateAuthorityName())
                .endEntityProfileName(configurationProperties.getEndEntityProfileName())
                .build();

        final CertificateResponse certificateResponse = ejbcaRestClient.callPkcs10Enroll(request);
        if (!"PEM".equals(certificateResponse.responseFormat())) {
            throw new IllegalStateException("Unexpected response format: " + certificateResponse.responseFormat());
        }

        return convertPemToX509Certificate(certificateResponse.certificate());
    }

    private static X509Certificate convertPemToX509Certificate(final String pemCertificate) throws IOException, CertificateException {
        try (final PEMParser pemParser = new PEMParser(new StringReader(pemCertificate))) {
            final Object parsedObj = pemParser.readObject();

            if (parsedObj instanceof X509CertificateHolder x509CertificateHolder) {
                return new JcaX509CertificateConverter()
                        .setProvider("BC")
                        .getCertificate(x509CertificateHolder);
            } else {
                throw new IllegalArgumentException("Invalid PEM content: not an X.509 certificate");
            }
        }
    }
}
