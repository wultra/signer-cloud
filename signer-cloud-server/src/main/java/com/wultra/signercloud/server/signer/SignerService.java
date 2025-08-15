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
package com.wultra.signercloud.server.signer;

import com.wultra.core.rest.client.base.RestClientException;
import com.wultra.signercloud.server.ejbca.EjbcaService;
import com.wultra.signercloud.server.powerauth.PowerAuthService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.Base64;

/**
 * Service for {@link Signer} operations.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Service
@AllArgsConstructor
@Slf4j
class SignerService {

    private final PowerAuthService powerAuthService;
    private final EjbcaService ejbcaService;
    private final SignerRepository signerRepository;

    SignerResponse createSigner(CreateSignerRequest request) {
        try {
            createSignerWithCertificate(request);
            return new SignerResponse(SignerResponseResult.OK, null);
        } catch (InactiveSignerException | RestClientException | CertificateException | IOException e) {
            logger.error("Exception during signer creation", e);
            return new SignerResponse(SignerResponseResult.FAIL, e.getMessage());
        }
    }

    private void createSignerWithCertificate(CreateSignerRequest request) throws RestClientException, CertificateException, IOException {
        final var externalSignerId = request.signerId();

        var isRegistrationActive = powerAuthService.isRegistrationActive(externalSignerId);
        if (!isRegistrationActive) {
            throw new InactiveSignerException("Signer registration is not active for external signer ID: " + externalSignerId);
        }

        final var csr = request.csr();
        var x509Certificate = ejbcaService.enrollCertificate(csr);

        final var certificate = Base64.getEncoder().encodeToString(x509Certificate.getEncoded());
        final var certificateExpiration = x509Certificate.getNotAfter().toInstant();

        final var signer = Signer.builder()
                .timestampCreated(Instant.now())
                .externalSignerId(request.signerId())
                .userId(request.userId())
                .csr(csr)
                .certificate(certificate)
                .timestampCertificateExpiration(certificateExpiration)
                .status(SignerStatus.ACTIVE)
                .build();

        signerRepository.save(signer);
    }
}
