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
import com.wultra.signercloud.server.utils.CertificateUtils;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
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
    private final IssuedCertificateRepository issuedCertificateRepository;

    /**
     * Marks all signers that have expired as expired and creates expiration callbacks if configured.
     *
     * @param limit Maximum number of signers to mark as expired.
     * @return Number of expired signers.
     */
    long cleanupSigners(final int limit) {
        final Instant now = Instant.now();
        final List<Signer> signers = signerRepository.markAsExpired(now, limit);

        if (configurationProperties.getExpiration().callbackEnabled()) {
            notifyCallbacks(signers, CallbackType.EXPIRED);
        }

        return signers.size();
    }

    private void notifyCallbacks(final List<Signer> signers, final CallbackType callbackType) {
        logger.info("Creating {} expiration callbacks.", signers.size());

        for (final Signer signer : signers) {
            final String callbackData = createCallbackData(signer, callbackType);
            callbackNotificationService.notify(callbackType, callbackData);
        }
    }

    private String createCallbackData(final Signer signer, final CallbackType callbackType) {
        final CallbackPayload payload = CallbackPayload.builder()
                .externalSignerId(signer.getExternalSignerId())
                .userId(signer.getUserId())
                .callbackType(callbackType)
                .certificateSerialNumber(convert(signer))
                .build();

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            logger.warn("Unable to serialize {} to JSON.", payload, e);
            return "{}";
        }
    }

    private static String convert(final Signer signer) {
        try {
            return signer.getX509Certificate().getSerialNumber().toString();
        } catch (final CertificateException e) {
            logger.error("Exception when parsing X509Certificate", e);
            return null;
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

        final var x509Certificate = enrollCertificate(externalSignerId, userId, csr);

        final var certificateBase64 = encodeCertificateToBase64(x509Certificate);
        final var certificateExpiration = x509Certificate.getNotAfter().toInstant();

        final var signerBuilder = signerRepository.findByExternalSignerId(externalSignerId)
                .map(this::updateSigner)
                .orElse(createSigner(externalSignerId));

        final var signer = signerBuilder
                .userId(userId)
                .csr(csr)
                .certificate(certificateBase64)
                .timestampCertificateExpiration(certificateExpiration)
                .status(SignerStatus.ACTIVE)
                .build();

        final var savedSigner = signerRepository.save(signer);
        saveIssuedCertificate(savedSigner.getId(), x509Certificate);
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

    private X509Certificate enrollCertificate(final String externalSignerId, final String userId, final String csr) {
        try {
            final var certificateRequest = EjbcaService.CertificateRequest.builder()
                    .csr(csr)
                    .externalSignerId(externalSignerId)
                    .userId(userId)
                    .build();

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

    private static String encodeCertificateToBase64(final X509Certificate certificate) {
        try {
            return Base64.getEncoder().encodeToString(certificate.getEncoded());
        } catch (final CertificateEncodingException e) {
            logger.warn("Exception when encoding certificate to base64", e);
            throw new CertificateEnrollmentException("Certificate could not be encoded: " + e.getMessage());
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

        final var issuedCertificate = IssuedCertificate.builder()
                .signerId(signerId)
                .timestampCreated(Instant.now())
                .serialNumber(serialNumber)
                .issuerDn(issuerDn)
                .timestampCertificateExpiration(certificateExpiration)
                .build();

        issuedCertificateRepository.save(issuedCertificate);
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
            revokeCertificates(signer);
        }

        final var updatedSigner = signer.toBuilder()
                .status(newStatus)
                .timestampLastUpdated(Instant.now())
                .build();

        signerRepository.save(updatedSigner);
    }

    private void revokeCertificates(final Signer signer) {
        try {
            final var currentCertificate = CertificateUtils.base64ToX509Certificate(signer.getCertificate());
            revokeCertificates(signer, currentCertificate);
        } catch (final CertificateException | IOException e) {
            logger.warn("Error when parsing current certificate", e);
            throw new CertificateRevocationException("Certificate could not be revoked: " + e.getMessage());
        }
    }

    private void revokeCertificates(final Signer signer, final X509Certificate currentCertificate) {
        final var certificatesToRevoke = issuedCertificateRepository.findForRevocation(signer.getId(), Instant.now());

        final var certificatesCount = certificatesToRevoke.size();
        var counter = 1;
        for (final var certificate : certificatesToRevoke) {
            logger.info("Revoking certificate {}/{} for externalSignerId={}", counter++, certificatesCount, signer.getExternalSignerId());

            final var request = new EjbcaService.RevokeCertificateRequest(
                    certificate.getSerialNumber(),
                    certificate.getIssuerDn());

            try {
                ejbcaService.revokeCertificate(request);
                logger.info("Certificate revoked");
            } catch (final RestClientException e) {
                logger.warn("Exception when revoking certificate", e);

                if (Objects.equals(certificate.getSerialNumber(), currentCertificate.getSerialNumber().toString()) &&
                        Objects.equals(certificate.getIssuerDn(), currentCertificate.getIssuerX500Principal().getName())) {
                    throw new CertificateRevocationException("Certificate could not be revoked because of EJBCA client error: " + e.getMessage());
                }
            }
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
    record CallbackPayload(String externalSignerId, String userId, CallbackType callbackType, String certificateSerialNumber) { }
}
