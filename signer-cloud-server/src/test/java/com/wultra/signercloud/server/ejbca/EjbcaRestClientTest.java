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

import com.wultra.core.rest.client.base.RestClient;
import com.wultra.core.rest.client.base.RestClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.MultiValueMap;

import java.util.Map;

import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link EjbcaRestClient}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@ExtendWith(MockitoExtension.class)
class EjbcaRestClientTest {

    private static final String SERIAL_NUMBER_HEX = "43148c1ac801facceb429395d80765c1c68f6a3c";
    private static final String ISSUER_DN = "CN=IssuingCA";

    @Mock
    private RestClient restClient;

    private EjbcaRestClient ejbcaRestClient;

    @BeforeEach
    void setUp() {
        ejbcaRestClient = new EjbcaRestClient(restClient);
    }

    @Test
    void testRevokeCertificateWhenValidRequestThenRestApiIsCalledWithCorrectValues() throws RestClientException {
        // given
        final var request = EjbcaService.RevokeCertificateRequest.builder()
                .issuerDN(ISSUER_DN)
                .serialNumberHex(SERIAL_NUMBER_HEX)
                .build();

        // when
        ejbcaRestClient.revokeCertificate(request);

        // then
        verify(restClient).put(
                "/v1/certificate/%s/%s/revoke".formatted(ISSUER_DN, SERIAL_NUMBER_HEX),
                null,
                MultiValueMap.fromSingleValue(Map.of("reason", "UNSPECIFIED")),
                null,
                ParameterizedTypeReference.forType(Void.class)
        );
    }
}
