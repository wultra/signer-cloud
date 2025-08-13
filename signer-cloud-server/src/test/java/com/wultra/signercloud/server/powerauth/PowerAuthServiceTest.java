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

import com.wultra.security.powerauth.client.PowerAuthClient;
import com.wultra.security.powerauth.client.model.enumeration.ActivationStatus;
import com.wultra.security.powerauth.client.model.error.PowerAuthClientException;
import com.wultra.security.powerauth.client.model.response.GetActivationStatusResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Test for {@link PowerAuthService}.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@ExtendWith(MockitoExtension.class)
class PowerAuthServiceTest {

    private static final String REGISTRATION_ID = "97ae0ada-4c9d-49e2-8ddf-ae46edfed4d2";

    @Mock
    private PowerAuthClient powerAuthClient;

    @InjectMocks
    private PowerAuthService tested;

    @Test
    void testIsRegistrationActive_successful() throws Exception {
        when(powerAuthClient.getActivationStatus(REGISTRATION_ID))
                .thenReturn(createResponse(ActivationStatus.ACTIVE));

        final boolean result = tested.isRegistrationActive(REGISTRATION_ID);

        assertTrue(result);
    }

    @Test
    void testIsRegistrationActive_negative_scenario() throws Exception {
        when(powerAuthClient.getActivationStatus(REGISTRATION_ID))
                .thenReturn(createResponse(ActivationStatus.BLOCKED));

        final boolean result = tested.isRegistrationActive(REGISTRATION_ID);

        assertFalse(result);
    }

    @Test
    void testIsRegistrationActive_exception() throws Exception {
        when(powerAuthClient.getActivationStatus(REGISTRATION_ID))
                .thenThrow(new PowerAuthClientException());

        final boolean result = tested.isRegistrationActive(REGISTRATION_ID);

        assertFalse(result);
    }

    private static GetActivationStatusResponse createResponse(final ActivationStatus activationStatus) {
        final GetActivationStatusResponse result = new GetActivationStatusResponse();
        result.setActivationStatus(activationStatus);
        return result;
    }
}
