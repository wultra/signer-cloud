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
import com.wultra.security.powerauth.client.model.error.PowerAuthClientException;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
     * Checks signature validity of the provided data.
     *
     * @param registrationId the unique identifier of the registration
     * @param data the signed data
     * @param signature the signature to verify
     * @return true if the signature is valid, false otherwise
     * @throws PowerAuthClientException in case of communication or processing error
     */
    public boolean isSignatureValid(final String registrationId, final String data, final String signature) throws PowerAuthClientException {
        logger.info("Verifying ECDSA signature for registrationId: {}", registrationId);
        final var response = powerAuthClient.verifyECDSASignature(registrationId, data, signature);
        final var isSignatureValid = response.isSignatureValid();
        logger.info("Signature is valid: {}", isSignatureValid);
        return isSignatureValid;
    }
}
