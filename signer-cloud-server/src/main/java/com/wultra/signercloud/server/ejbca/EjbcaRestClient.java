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

import com.wultra.core.rest.client.base.DefaultRestClient;
import com.wultra.core.rest.client.base.RestClient;
import com.wultra.core.rest.client.base.RestClientConfiguration;
import com.wultra.core.rest.client.base.RestClientException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Rest client for EJBCA.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 * @see <a href="https://docs.keyfactor.com/ejbca/9.0/open-api-specification">EJBCA Open API Specification</a>
 */
@Component
@Slf4j
class EjbcaRestClient {

    private final RestClient restClient;

    public EjbcaRestClient(final EjbcaConfigurationProperties properties) throws RestClientException {
        validateConfiguration(properties.getRestClientConfiguration());
        this.restClient = new DefaultRestClient(properties.getRestClientConfiguration());
    }

    private void validateConfiguration(final RestClientConfiguration restClientConfiguration) {
        if (restClientConfiguration.isCertificateAuthEnabled()) {
            // unfortunately, wultra-core 1.12.0 validates only non-null values, so we need to check manually non-blank ENV
            if (StringUtils.isBlank(restClientConfiguration.getKeyStorePassword())) {
                throw new BeanCreationException("Keystore password is not configured");
            }
            if (StringUtils.isBlank(restClientConfiguration.getKeyAlias())) {
                throw new BeanCreationException("Keystore key alias is not configured");
            }
            if (StringUtils.isBlank(restClientConfiguration.getKeyPassword())) {
                throw new BeanCreationException("Keystore key password is not configured");
            }
        }
    }

    public CertificateResponse callPkcs10Enroll(final CertificateRequest request) throws RestClientException {
        logger.info("Sending certificate request to EJBCA");
        logger.debug("Sending certificate request to EJBCA: {}", request);
        final ResponseEntity<CertificateResponse> response = restClient.post("/v1/certificate/pkcs10enroll", request, ParameterizedTypeReference.forType(CertificateResponse.class));
        logger.info("Got certificate response, status code: {}", response.getStatusCode());
        logger.debug("Got certificate response: {}", response.getBody());
        return response.getBody();
    }

    /**
     * Revokes a certificate.
     *
     * @param parameters parameters identifying the certificate to be revoked
     * @return the response containing revocation details
     * @throws RestClientException if an error occurs during the revocation process
     */
    public CertificateRevocationResponse revokeCertificate(final CertificateRevocationParameters parameters) throws RestClientException {
        final String url = UriComponentsBuilder.fromPath("/v1/certificate/{issuer_dn}/{certificate_serial_number}/revoke")
                .buildAndExpand(parameters.issuerDn(), parameters.certificateSerialNumber())
                .toUriString();

        logger.info("Revoking certificate, serial number: {}", parameters.certificateSerialNumber());
        final ResponseEntity<CertificateRevocationResponse> response = restClient.put(url, new Object(), ParameterizedTypeReference.forType(CertificateRevocationResponse.class));

        Assert.notNull(response.getBody(), "Response body must not be null");
        logger.info("Got revocation certificate response, revoked: {}", response.getBody().revoked());
        logger.debug("Got revocation certificate response: {}", response.getBody());
        return response.getBody();
    }
}
