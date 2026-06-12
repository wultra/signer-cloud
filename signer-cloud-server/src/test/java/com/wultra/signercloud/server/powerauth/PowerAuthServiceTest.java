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
package com.wultra.signercloud.server.powerauth;

import com.wultra.security.powerauth.client.v3.PowerAuthClient;
import com.wultra.security.powerauth.client.model.error.PowerAuthClientException;
import com.wultra.security.powerauth.client.model.request.v3.VerifyECDSASignatureRequest;
import com.wultra.security.powerauth.client.model.response.v3.VerifyECDSASignatureResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Test for {@link PowerAuthService}.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@ExtendWith(MockitoExtension.class)
class PowerAuthServiceTest {

    private static final String REGISTRATION_ID = "97ae0ada-4c9d-49e2-8ddf-ae46edfed4d2";
    private static final String DATA = "data";
    private static final String SIGNATURE = "signature";

    @Mock
    private PowerAuthClient powerAuthClient;

    @InjectMocks
    private PowerAuthService tested;

    @Test
    void testIsSignatureValidWhenClientThrowsExceptionThenExceptionIsPropagated() throws Exception {
        // given
        final var request = buildPowerAuthRequest();

        when(powerAuthClient.verifyECDSASignature(request))
                .thenThrow(new PowerAuthClientException());

        // when / then
        assertThrows(PowerAuthClientException.class, () -> tested.isSignatureValid(request));
    }

    @Test
    void testIsSignatureValidWhenSignatureIsNotValidThenFalseIsReturned() throws Exception {
        // given
        final var request = buildPowerAuthRequest();
        final var powerAuthResponse = VerifyECDSASignatureResponse.builder()
                        .build();

        when(powerAuthClient.verifyECDSASignature(request)).thenReturn(powerAuthResponse);

        // when
        final var response = tested.isSignatureValid(request);

        // then
        assertFalse(response);
    }

    @Test
    void testIsSignatureValidWhenSignatureIsValidThenTrueIsReturned() throws Exception {
        // given
        final var request = buildPowerAuthRequest();
        final var powerAuthResponse = VerifyECDSASignatureResponse.builder()
                .signatureValid(true)
                .build();

        when(powerAuthClient.verifyECDSASignature(request)).thenReturn(powerAuthResponse);

        // when
        final var response = tested.isSignatureValid(request);

        // then
        assertTrue(response);
    }

    private VerifyECDSASignatureRequest buildPowerAuthRequest() {
        final var request = new VerifyECDSASignatureRequest();
        request.setActivationId(REGISTRATION_ID);
        request.setData(DATA);
        request.setSignature(SIGNATURE);
        return request;
    }
}
