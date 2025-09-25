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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wultra.core.rest.client.base.RestClientException;
import com.wultra.security.powerauth.client.model.error.PowerAuthClientException;
import com.wultra.security.powerauth.client.model.request.VerifyECDSASignatureRequest;
import com.wultra.signercloud.server.callback.api.CallbackNotificationService;
import com.wultra.signercloud.server.callback.api.CallbackType;
import com.wultra.signercloud.server.ejbca.EjbcaService;
import com.wultra.signercloud.server.powerauth.PowerAuthService;
import com.wultra.signercloud.server.restapi.Try;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;

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
    private final CallbackNotificationService callbackNotificationService;
    private final ObjectMapper objectMapper;
    private final IssuedCertificateMetadataRepository issuedCertificateMetadataRepository;
    private final CertificateRevocationService certificateRevocationService;

    /**
     * Marks all signers that have expired as expired and creates expiration callbacks if configured.
     *
     * @param limit Maximum number of signers to mark as expired.
     * @return Number of expired signers.
     */
    long cleanupSigners(final int limit) {
        final List<Signer> signers = signerRepository.markAsExpired(limit);

        if (callbackNotificationService.isCallbackEnabled(CallbackType.EXPIRED)) {
            logger.info("Creating {} expiration callbacks.", signers.size());
            for (final Signer signer : signers) {
                notifyExpiredCallback(signer);
            }
        }

        return signers.size();
    }

    private void notifyExpiredCallback(final Signer signer) {
        final X509CertificateMetadata x509CertificateMetadata = convert(signer);
        final CallbackPayload payload = CallbackPayload.builder()
                .externalSignerId(signer.getExternalSignerId())
                .userId(signer.getUserId())
                .callbackType(CallbackType.EXPIRED)
                .certificateSerialNumber(x509CertificateMetadata.serialNumber())
                .certificateExpiration(x509CertificateMetadata.expiration())
                .build();
        notifyCallback(payload);
    }

    /**
     * Renew all signers that are about to expire and creates renewal callbacks if configured.
     *
     * @param limit Maximum number of signers to be renewed.
     * @return Number of renewed signers.
     */
    long renewSigners(final int limit) {
        final Instant expirationThreshold = Instant.now().plus(configurationProperties.getRenewal().threshold());
        final List<Signer> signers = signerRepository.findForRenewal(expirationThreshold, limit);

        for (final Signer signer : signers) {
            renewSigner(signer);
        }

        return signers.size();
    }

    private void renewSigner(final Signer signer) {
        final var ejbcaCertificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(signer.getCsr())
                .externalSignerId(signer.getExternalSignerId())
                .userId(signer.getUserId())
                .build();

        final var certificateResponse = enrollCertificate(ejbcaCertificateRequest);
        final var x509Certificate = certificateResponse.certificate();
        final var chain = certificateResponse.chain();

        try {
            signerRepository.save(signer.toBuilder()
                    .timestampCertificateExpiration(x509Certificate.getNotAfter().toInstant())
                    .timestampLastUpdated(Instant.now())
                    .certificateFromX509(x509Certificate)
                    .certificateChainFromList(chain)
                    .build());

            saveIssuedCertificate(signer.getId(), x509Certificate);
        } catch (final CertificateEncodingException e) {
            logger.warn("Exception when encoding certificate to base64 during renewal, externalSignerId: {}", signer.getExternalSignerId());
            throw new CertificateEnrollmentException("Certificate could not be encoded during renewal", e);
        }

        if (callbackNotificationService.isCallbackEnabled(CallbackType.RENEWED)) {
            notifyRenewalCallback(signer, x509Certificate);
        }
    }

    private void notifyRenewalCallback(final Signer signer, final X509Certificate x509Certificate) {
        final CallbackPayload payload = CallbackPayload.builder()
                .externalSignerId(signer.getExternalSignerId())
                .userId(signer.getUserId())
                .callbackType(CallbackType.RENEWED)
                .certificateSerialNumber(x509Certificate.getSerialNumber().toString())
                .certificateExpiration(x509Certificate.getNotAfter().toInstant())
                .build();
        notifyCallback(payload);
    }

    private void notifyCallback(final CallbackPayload callbackPayload) {
        callbackNotificationService.notify(callbackPayload.callbackType(), convert(callbackPayload));
    }

    private String convert(final CallbackPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            logger.warn("Unable to serialize {} to JSON.", payload, e);
            return "{}";
        }
    }

    private static X509CertificateMetadata convert(final Signer signer) {
        try {
            final X509Certificate x509Certificate = signer.getX509Certificate();
            return new X509CertificateMetadata(x509Certificate.getSerialNumber().toString(), x509Certificate.getNotAfter().toInstant());
        } catch (final CertificateException e) {
            logger.error("Exception when parsing X509Certificate", e);
            return new X509CertificateMetadata(null, null);
        }
    }

    /**
     * Creates a new {@link Signer} or updates an existing one if it already exists (based on {@link Signer#getExternalSignerId}).
     * This method verifies signature in {@link CreateUpdateSignerRequest#csr()} via PowerAuth server, then generates
     * a certificate via the EJBCA service and finally stores the signer in the database.
     *
     * @param request the request containing details of signer
     * @return result of operation as {@link Try}
     */
    Try<Void> createUpdateSigner(final CreateUpdateSignerRequest request) {
        try {
            processCreateUpdateSigner(request);
            return Try.success();
        } catch (final SignatureVerificationException | CertificateEnrollmentException e) {
            return Try.error(e);
        }
    }

    private void processCreateUpdateSigner(final CreateUpdateSignerRequest request) {
        final var externalSignerId = request.signerId();
        final var userId = request.userId();
        final var csr = request.csr();

        verifySignature(externalSignerId, csr);

        final var ejbcaCertificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(csr)
                .externalSignerId(externalSignerId)
                .userId(userId)
                .build();

        final var certificateResponse = enrollCertificate(ejbcaCertificateRequest);
        final var x509Certificate = certificateResponse.certificate();
        final var chain = certificateResponse.chain();

        final var signerBuilder = signerRepository.findByExternalSignerId(externalSignerId)
                .map(this::updateSigner)
                .orElse(createSigner(externalSignerId));

        try {
            final var signer = signerBuilder
                    .userId(userId)
                    .csr(csr)
                    .certificateFromX509(x509Certificate)
                    .timestampCertificateExpiration(x509Certificate.getNotAfter().toInstant())
                    .status(SignerStatus.ACTIVE)
                    .certificateChainFromList(chain)
                    .build();
            final var savedSigner = signerRepository.save(signer);
            saveIssuedCertificate(savedSigner.getId(), x509Certificate);
        } catch (final CertificateEncodingException e) {
            logger.warn("Exception when encoding certificate to base64 during creation/update, externalSignerId: {}", externalSignerId);
            throw new CertificateEnrollmentException("Certificate could not be encoded during creation/update", e);
        }
    }

    private void verifySignature(final String externalSignerId, final String csrBase64) {
        try {
            final var csrBytes = Base64.getDecoder().decode(csrBase64);
            final var csr = new PKCS10CertificationRequest(csrBytes);

            final var signature = csr.getSignature();
            final var signatureBase64 = Base64.getEncoder().encodeToString(signature);

            final var data = csr.toASN1Structure()
                    .getCertificationRequestInfo()
                    .getEncoded();
            final var dataBase64 = Base64.getEncoder().encodeToString(data);

            final var request = new VerifyECDSASignatureRequest();
            request.setActivationId(externalSignerId);
            request.setData(dataBase64);
            request.setSignature(signatureBase64);

            final var isSignatureValid = powerAuthService.isSignatureValid(request);
            if (!isSignatureValid) {
                throw new SignatureVerificationException("Signature is not valid. External signer ID: " + externalSignerId);
            }
        } catch (final PowerAuthClientException e) {
            logger.warn("Error response from PowerAuth server", e);
            throw new SignatureVerificationException("Signature could not be verified due to PowerAuth error: " + e.getMessage());
        } catch (final IOException e) {
            logger.warn("Error when processing CSR", e);
            throw new SignatureVerificationException("Error when processing CSR: " + e.getMessage());
        }
    }

    private EjbcaService.CertificateResponse enrollCertificate(final EjbcaService.CertificateRequest certificateRequest) {
        try {
            return ejbcaService.enrollCertificate(certificateRequest);
        } catch (final RestClientException e) {
            logger.warn("Error response from EJBCA server", e);
            throw new CertificateEnrollmentException("Certificate could not be enrolled due to EJBCA error: " + e.getMessage());
        } catch (final CertificateException e) {
            logger.warn("Error when processing enrolled certificate", e);
            throw new CertificateEnrollmentException("Certificate could not be processed: " + e.getMessage());
        } catch (final IOException e) {
            logger.warn("Error when reading enrolled certificate", e);
            throw new CertificateEnrollmentException("Certificate could not be read: " + e.getMessage());
        }
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

    private void saveIssuedCertificate(final long signerId, final X509Certificate x509Certificate) {
        final var certificateExpiration = x509Certificate.getNotAfter().toInstant();
        final var serialNumber = x509Certificate.getSerialNumber().toString();
        final var issuerDn = x509Certificate.getIssuerX500Principal().getName();

        final var issuedCertificate = IssuedCertificateMetadata.builder()
                .signer(AggregateReference.to(signerId))
                .timestampCreated(Instant.now())
                .serialNumber(serialNumber)
                .issuerDn(issuerDn)
                .timestampCertificateExpiration(certificateExpiration)
                .status(IssuedCertificateStatus.ISSUED)
                .build();

        issuedCertificateMetadataRepository.save(issuedCertificate);
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
            processUpdateStatus(externalSignerId, request);
            return Try.success();
        } catch (final SignerNotFoundException | SignerStatusTransitionException | CertificateRevocationException e) {
            return Try.error(e);
        }
    }

    private void processUpdateStatus(final String externalSignerId, final UpdateSignerStatusRequest request) {
        final var signer = signerRepository.findByExternalSignerId(externalSignerId)
                .orElseThrow(() -> new SignerNotFoundException("Signer not found for external signer ID: " + externalSignerId));

        final var oldStatus = signer.getStatus();
        final var newStatus = request.signerStatus();

        if (oldStatus == newStatus) {
            return;
        }

        final var isTransitionValid = VALID_STATUS_TRANSITIONS.getOrDefault(oldStatus, EnumSet.noneOf(SignerStatus.class))
                .contains(newStatus);

        if (!isTransitionValid) {
            throw new SignerStatusTransitionException("Invalid status transition from %s to %s".formatted(oldStatus, newStatus));
        }

        if (newStatus == SignerStatus.REVOKED) {
            final var revocationReason = Optional.ofNullable(request.revocationReason())
                            .orElse(RevocationReason.UNSPECIFIED);
            revokeCertificates(signer, revocationReason);
        }

        final var updatedSigner = signer.toBuilder()
                .status(newStatus)
                .timestampLastUpdated(Instant.now())
                .build();

        signerRepository.save(updatedSigner);
    }

    private void revokeCertificates(final Signer signer, final RevocationReason revocationReason) {
        final var signerId = signer.getId();

        final var certificatesMetadata = issuedCertificateMetadataRepository.findForRevocation(signerId);
        final var certificatesToRevokeCount = certificatesMetadata.size();

        for (var i = 0; i < certificatesToRevokeCount; i++) {
            logger.info("Revoking certificate {}/{} in EJBCA", i + 1, certificatesToRevokeCount);
            final var certificateMetadata = certificatesMetadata.get(i);
            certificateRevocationService.revokeCertificate(certificateMetadata, revocationReason);
        }
    }

    /**
     * Get details of {@link Signer}.
     *
     * @param externalSignerId identifier of the signer to get details for
     * @return result as {@link Try} containing {@link SignerDetailResponse} or an error
     */
    Try<SignerDetailResponse> getDetail(final String externalSignerId) {
        return signerRepository.findByExternalSignerId(externalSignerId)
                .map(signer -> new SignerDetailResponse(signer.getExternalSignerId(), signer.getUserId(), signer.getStatus()))
                .map(Try::success)
                .orElse(Try.error(new SignerNotFoundException("Signer not found: " + externalSignerId)));
    }

    @Builder
    private record CallbackPayload(String externalSignerId, String userId, CallbackType callbackType, String certificateSerialNumber, Instant certificateExpiration) { }

    private record X509CertificateMetadata(String serialNumber, Instant expiration) {}
}
