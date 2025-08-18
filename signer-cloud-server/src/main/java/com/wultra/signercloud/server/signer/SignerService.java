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
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Creates a new {@link Signer} or updates an existing one if it already exists (based on {@link Signer#externalSignerId}).
     * This method checks whether the registration in PowerAuth is active, then generates a certificate via the EJBCA service
     * (based on {@link CreateUpdateSignerRequest#csr}), and finally stores the signer in the database.
     *
     * @param request the request containing details of signer
     * @return {@link SignerResponse} indicating the result of the operation.
     */
    @Transactional
    SignerResponse createUpdateSigner(CreateUpdateSignerRequest request) {
        try {
            logger.info("action: createUpdateSigner, state: initiated, userId: {}, externalSignerId: {}", request.userId(), request.signerId());
            createUpdateSignerWithCertificate(request);
            logger.info("action: createUpdateSigner, state: succeeded");
            return new SignerResponse(SignerResponseResult.OK, null);
        } catch (InactiveSignerException | RestClientException | CertificateException | IOException e) {
            logger.info("action: createUpdateSigner, state: failed, errorMessage: {}", e.getMessage());
            return new SignerResponse(SignerResponseResult.FAIL, e.getMessage());
        }
    }

    private void createUpdateSignerWithCertificate(CreateUpdateSignerRequest request) throws RestClientException, CertificateException, IOException {
        final var externalSignerId = request.signerId();

        var isRegistrationActive = powerAuthService.isRegistrationActive(externalSignerId);
        if (!isRegistrationActive) {
            throw new InactiveSignerException("Signer registration is not active for external signer ID: " + externalSignerId);
        }

        final var csr = request.csr();
        final var userId = request.userId();
        final var certificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(csr)
                .externalSignerId(externalSignerId)
                .userId(userId)
                .build();

        var x509Certificate = ejbcaService.enrollCertificate(certificateRequest);

        final var certificate = Base64.getEncoder().encodeToString(x509Certificate.getEncoded());
        final var certificateExpiration = x509Certificate.getNotAfter().toInstant();

        final var signerBuilder = signerRepository.findByExternalSignerId(externalSignerId)
                .map(this::updateSigner)
                .orElse(createSigner(externalSignerId));

        final var signer = signerBuilder
                .userId(userId)
                .csr(csr)
                .certificate(certificate)
                .timestampCertificateExpiration(certificateExpiration)
                .status(SignerStatus.ACTIVE)
                .build();

        signerRepository.save(signer);
    }

    private Signer.SignerBuilder createSigner(String externalSignerId) {
        return Signer.builder()
                .timestampCreated(Instant.now())
                .externalSignerId(externalSignerId);
    }

    private Signer.SignerBuilder updateSigner(Signer signer) {
        return signer.toBuilder()
                .timestampCreated(Instant.now());
    }
}
