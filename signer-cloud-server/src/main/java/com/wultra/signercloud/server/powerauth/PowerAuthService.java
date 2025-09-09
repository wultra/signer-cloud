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
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * PowerAuth service.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Service
@AllArgsConstructor
@Slf4j
public class PowerAuthService {

    private final PowerAuthClient powerAuthClient;

    /**
     * Checks if the registration is currently active based on its status.
     *
     * @param registrationId the unique identifier of the registration to check
     * @return {@code true} if the registration is active, {@code false} otherwise
     */
    public boolean isRegistrationActive(final String registrationId){
        return fetchActivationStatus(registrationId)
                .filter(ActivationStatus.ACTIVE::equals)
                .isPresent();
    }

    private Optional<ActivationStatus> fetchActivationStatus(final String registrationId) {
        try {
            logger.info("Retrieving status of registrationId: {}", registrationId);
            final GetActivationStatusResponse response = powerAuthClient.getActivationStatus(registrationId);
            final ActivationStatus activationStatus = response.getActivationStatus();
            logger.info("Got status: {} of registrationId: {}", activationStatus, registrationId);
            return Optional.of(activationStatus);
        } catch (final PowerAuthClientException e) {
            logger.warn("Unable to get activation status of registrationId: {}", registrationId, e);
            return Optional.empty();
        }
    }

    public boolean isSignatureValid(final String registrationId, final String data, final String signature) throws PowerAuthClientException {
        final var response = powerAuthClient.verifyECDSASignature(registrationId, data, signature);
        return response.isSignatureValid();
    }
}
