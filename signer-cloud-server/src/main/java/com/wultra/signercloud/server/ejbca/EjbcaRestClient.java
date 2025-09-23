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

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.wultra.core.rest.client.base.DefaultRestClient;
import com.wultra.core.rest.client.base.RestClient;
import com.wultra.core.rest.client.base.RestClientConfiguration;
import com.wultra.core.rest.client.base.RestClientException;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

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

    @Autowired
    public EjbcaRestClient(final EjbcaConfigurationProperties properties) throws RestClientException {
        validateConfiguration(properties.getRestClientConfiguration());
        this.restClient = new DefaultRestClient(properties.getRestClientConfiguration());
    }

    /**
     * Constructor for unit testing purposes.
     * @param restClient Rest client to be used.
     */
    EjbcaRestClient(final RestClient restClient) {
        this.restClient = restClient;
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
     * Revokes single certificate in EJBCA.
     *
     * @param request metadata for certificate to be revoked
     * @throws RestClientException in case of communication error with EJBCA
     */
    void revokeCertificate(final EjbcaService.RevokeCertificateRequest request) throws RestClientException {
        final var serialNumber = request.serialNumberHex();
        final var issuerDN = request.issuerDN();

        final var url = UriComponentsBuilder.fromPath("/v1/certificate/{issuerDN}/{serialNumber}/revoke")
                        .buildAndExpand(issuerDN, serialNumber)
                        .toUriString();

        final var params = MultiValueMap.fromSingleValue(Map.of("reason", "UNSPECIFIED"));

        logger.info("Revoking certificate, serialNumberHex: {}, issuerDN: {}", request.serialNumberHex(), request.issuerDN());
        restClient.put(url, null, params, null, ParameterizedTypeReference.forType(Void.class));
        logger.info("Successful EJBCA certificate revoke call");
    }

    @Jacksonized
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CertificateRequest(
            String accountBindingId,
            String username,
            String certificateRequest,
            String certificateProfileName,
            String endEntityProfileName,
            String certificateAuthorityName,
            boolean includeChain
    ) {
    }

    @Jacksonized
    @Builder
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record CertificateResponse(
            String responseFormat,
            String certificate,
            String serialNumber,
            List<String> certificateChain
    ) {
    }
}
