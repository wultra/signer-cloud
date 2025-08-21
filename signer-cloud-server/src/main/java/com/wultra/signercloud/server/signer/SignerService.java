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
import com.wultra.signercloud.server.callback.CallbackEvent;
import com.wultra.signercloud.server.callback.CallbackService;
import com.wultra.signercloud.server.ejbca.EjbcaService;
import com.wultra.signercloud.server.powerauth.PowerAuthService;
import com.wultra.signercloud.server.restapi.Try;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.*;
import java.util.List;

/**
 * Service for {@link Signer} operations.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Service
@Transactional
@AllArgsConstructor
@Slf4j
class SignerService {

    private static final Map<SignerStatus, EnumSet<SignerStatus>> VALID_STATUS_TRANSITIONS = Map.of(
            SignerStatus.ACTIVE, EnumSet.of(SignerStatus.BLOCKED, SignerStatus.REMOVED, SignerStatus.REVOKED),
            SignerStatus.BLOCKED, EnumSet.of(SignerStatus.ACTIVE, SignerStatus.REMOVED, SignerStatus.REVOKED)
    );

    private final PowerAuthService powerAuthService;
    private final EjbcaService ejbcaService;
    private final SignerRepository signerRepository;
    private final SignerConfigurationProperties configurationProperties;
    private final CallbackService callbackService;

    /**
     * Creates a new {@link Signer} or updates an existing one if it already exists (based on {@link Signer#getExternalSignerId}).
     * This method checks whether the registration in PowerAuth is active, then generates a certificate via the EJBCA service
     * (based on {@link CreateUpdateSignerRequest#csr()}), and finally stores the signer in the database.
     *
     * @param request the request containing details of signer
     * @return result of operation as {@link Try}
     */
    Try<Void> createUpdateSigner(final CreateUpdateSignerRequest request) {
        try {
            createUpdateSignerWithCertificate(request);
            return Try.success();
        } catch (final InactiveSignerException | RestClientException | CertificateException | IOException e) {
            return Try.error(e);
        }
    }

    /**
     * Marks all signers that have expired as expired and creates expiration callbacks if configured.
     *
     * @return Number of expired signers.
     */
    // TODO Lubos test
    long cleanupSigners() {
        final Instant now = Instant.now();
        // TODO Lubos limit size
        final List<Long> ids = signerRepository.markAsExpired(now);

        if (configurationProperties.getExpiration().enabled()) {
            logger.info("Creating {} expiration callbacks.", ids.size());
            // TODO Lubos create callback
            callbackService.save(CallbackEvent.builder()
                    .build());
        }

        return ids.size();
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
    Try<Void> updateStatus(final String externalSignerId, final UpdateSignerStatusRequest request) {
        try {
            updateStatus(externalSignerId, request.signerStatus());
            return Try.success();
        } catch (SignerNotFoundException | SignerStatusTransitionException | RestClientException e) {
            return Try.error(e);
        }
    }

    private void updateStatus(final String externalSignerId, final SignerStatus newStatus) throws RestClientException {
        final var signer = signerRepository.findByExternalSignerId(externalSignerId)
                .orElseThrow(() -> new SignerNotFoundException("Signer not found for external signer ID: " + externalSignerId));

        final var oldStatus = signer.getStatus();

        if (oldStatus == newStatus) {
            return;
        }

        final var isTransitionValid = VALID_STATUS_TRANSITIONS.getOrDefault(oldStatus, EnumSet.noneOf(SignerStatus.class))
                .contains(newStatus);

        if (!isTransitionValid) {
            throw new SignerStatusTransitionException("Invalid status transition from %s to %s".formatted(oldStatus, newStatus));
        }

        if (newStatus == SignerStatus.REVOKED) {
            ejbcaService.revokeCertificates(externalSignerId);
        }

        final var updatedSigner = signer.toBuilder()
                .status(newStatus)
                .timestampLastUpdated(Instant.now())
                .build();

        signerRepository.save(updatedSigner);
    }

    /**
     * Get details of {@link Signer}.
     *
     * @param externalSignerId identifier of the signer to get details for
     * @return result as {@link Try} containing {@link SignerDetailResponse} or an error
     */
    @Transactional
    Try<SignerDetailResponse> getDetail(final String externalSignerId) {
        return signerRepository.findByExternalSignerId(externalSignerId)
                .map(signer -> new SignerDetailResponse(signer.getExternalSignerId(), signer.getUserId(), signer.getStatus()))
                .map(Try::success)
                .orElse(Try.error(new SignerNotFoundException("Signer not found: " + externalSignerId)));
    }
}
