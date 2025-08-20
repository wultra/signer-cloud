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
import com.wultra.signercloud.server.restapi.Try;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Service for {@link Signer} operations.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Service
@AllArgsConstructor
class SignerService {

    private static final Map<SignerStatus, Set<SignerStatus>> VALID_STATUS_TRANSITIONS = Map.of(
            SignerStatus.ACTIVE, Set.of(SignerStatus.BLOCKED, SignerStatus.REMOVED, SignerStatus.REVOKED),
            SignerStatus.BLOCKED, Set.of(SignerStatus.ACTIVE, SignerStatus.REMOVED, SignerStatus.REVOKED)
    );

    private final PowerAuthService powerAuthService;
    private final EjbcaService ejbcaService;
    private final SignerRepository signerRepository;

    /**
     * Creates a new {@link Signer} or updates an existing one if it already exists (based on {@link Signer#externalSignerId}).
     * This method checks whether the registration in PowerAuth is active, then generates a certificate via the EJBCA service
     * (based on {@link CreateUpdateSignerRequest#csr}), and finally stores the signer in the database.
     *
     * @param request the request containing details of signer
     * @return result of operation as {@link Try}
     */
    @Transactional
    Try createUpdateSigner(final CreateUpdateSignerRequest request) {
        try {
            createUpdateSignerWithCertificate(request);
            return Try.success();
        } catch (InactiveSignerException | RestClientException | CertificateException | IOException e) {
            return Try.error(e);
        }
    }

    private void createUpdateSignerWithCertificate(final CreateUpdateSignerRequest request) throws RestClientException, CertificateException, IOException {
        final var externalSignerId = request.signerId();

        final var isRegistrationActive = powerAuthService.isRegistrationActive(externalSignerId);
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

        final var x509Certificate = ejbcaService.enrollCertificate(certificateRequest);

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

    private Signer.SignerBuilder createSigner(final String externalSignerId) {
        return Signer.builder()
                .timestampCreated(Instant.now())
                .externalSignerId(externalSignerId);
    }

    private Signer.SignerBuilder updateSigner(final Signer signer) {
        return signer.toBuilder()
                .timestampLastUpdated(Instant.now());
    }

    /**
     * Updates the {@link SignerStatus} of a {@link Signer}.
     *
     * @param externalSignerId identifier of the signer to update
     * @param request request containing the new status
     * @return result of operation as {@link Try}
     */
    @Transactional
    Try updateStatus(final String externalSignerId, final UpdateSignerStatusRequest request) {
        try {
            updateStatus(externalSignerId, request.signerStatus());
            return Try.success();
        } catch (SignerNotFoundException | SignerStatusTransitionException e) {
            return Try.error(e);
        }
    }

    private void updateStatus(final String externalSignerId, final SignerStatus newStatus) {
        var signer = signerRepository.findByExternalSignerId(externalSignerId)
                .orElseThrow(() -> new SignerNotFoundException("Signer not found for external signer ID: " + externalSignerId));

        final var oldStatus = signer.getStatus();

        if (oldStatus == newStatus) {
            throw new SignerStatusTransitionException("Signer status is already: " + newStatus);
        }

        final var isTransitionValid = VALID_STATUS_TRANSITIONS.getOrDefault(oldStatus, Collections.emptySet())
                .contains(newStatus);

        if (!isTransitionValid) {
            throw new SignerStatusTransitionException("Invalid status transition from " + oldStatus + " to " + newStatus);
        }

        if (newStatus == SignerStatus.REVOKED) {
            // TODO: Call EJBCA to revoke the certificate
        }

        signer = signer.toBuilder()
                .status(newStatus)
                .build();

        signerRepository.save(signer);
    }
}
