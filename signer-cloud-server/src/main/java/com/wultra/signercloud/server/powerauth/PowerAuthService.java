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
     * @param request Request containing registrationId, data and signature to verify
     * @return true if the signature is valid, false otherwise
     * @throws PowerAuthClientException in case of communication or processing error
     */
    public boolean isSignatureValid(final VerifyECDSASignatureRequest request) throws PowerAuthClientException {
        logger.info("Verifying ECDSA signature for registrationId: {}", request.getActivationId());
        final var response = powerAuthClient.verifyECDSASignature(request);
        final var isSignatureValid = response.isSignatureValid();
        logger.info("Signature is valid: {}", isSignatureValid);
        return isSignatureValid;
    }
}
