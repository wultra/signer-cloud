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
package com.wultra.signercloud.server.document;

import com.wultra.signercloud.server.signer.*;
import com.wultra.signercloud.server.utils.CertificateUtils;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.ContentType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
/**
 * Document service.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Service
@AllArgsConstructor
@Slf4j
@Transactional
class DocumentService {
    private static final String DOCUMENT_DOWNLOAD_PATH = "/documents/{documentId}/download";

    private final DocumentConfigurationProperties configurationProperties;
    private final DocumentRepository documentRepository;
    private final DocumentContentRepository documentContentRepository;
    private final SignerRepository signerRepository;
    private final PAdESService padesService;

    /**
     * Cleanup documents.
     *
     * @return statistics of the cleanup operation
     * @implNote This method is intended to be called by a scheduled job to clean up
     */
    CleanupResult cleanupDocuments() {
        final Instant now = Instant.now();
        final CleanupResult.CleanupResultBuilder cleanupResultBuilder = CleanupResult.builder();

        processRetention(
                DocumentStatus.REJECTED,
                configurationProperties.getRejected().getRetentionPeriod(),
                cleanupResultBuilder::rejectedDocuments,
                now
        );

        processRetention(
                DocumentStatus.SIGNED,
                configurationProperties.getSigned().getRetentionPeriod(),
                cleanupResultBuilder::signedDocuments,
                now
        );

        processRetention(
                DocumentStatus.WAITING,
                configurationProperties.getWaiting().getRetentionPeriod(),
                cleanupResultBuilder::waitingDocuments,
                now
        );

        return cleanupResultBuilder.build();
    }

    private void processRetention(final DocumentStatus status, final Duration retentionPeriod, final Consumer<String> resultConsumer, final Instant now) {
        if (retentionPeriod != null) {
            final long deletedCount = documentRepository.deleteByStatusAndTimestampCreatedBefore(status, now.minus(retentionPeriod));
            resultConsumer.accept(String.valueOf(deletedCount));
        } else {
            resultConsumer.accept("disabled");
        }
    }

    /**
     * Stores the {@link Document} for signing and calculates its SHA-256 hash.
     *
     * @param externalSignerId {@link Signer#getExternalSignerId()}
     * @param externalDocumentId unique identifier of the document in the external system
     * @param documentName name of the document
     * @param file the PDF document to be stored for signing
     * @return response with uploaded document details
     */
    UploadDocumentResponse uploadDocument(
            final String externalSignerId,
            final String externalDocumentId,
            final String documentName,
            final MultipartFile file
    ) {
        final var contentType = file.getContentType();
        if (!ContentType.APPLICATION_PDF.getMimeType().equals(contentType)) {
            throw new DocumentUploadException("Unsupported content type: " + contentType);
        }

        final var signer = signerRepository.findByExternalSignerId(externalSignerId)
                .orElseThrow(() -> new SignerNotFoundException(externalSignerId));

        final var fileName = file.getOriginalFilename();
        final var fileSize = getFileSize(file);
        final var fileContent = getFileBytes(file);
        final var hash = computeHash(fileContent, configurationProperties.getContentHashAlgorithm());

        final var documentContent = DocumentContent.builder()
                .content(fileContent)
                .build();

        final var savedDocumentContent = documentContentRepository.save(documentContent);

        final var document = Document.builder()
                .timestampCreated(Instant.now())
                .documentId(UUID.randomUUID().toString())
                .externalId(externalDocumentId)
                .signer(AggregateReference.to(signer.getId()))
                .documentName(documentName)
                .fileName(fileName)
                .fileSize(fileSize)
                .documentContent(AggregateReference.to(savedDocumentContent.getId()))
                .hash(hash)
                .status(DocumentStatus.WAITING)
                .build();

        documentRepository.save(document);

        return UploadDocumentResponse.builder()
                .documentId(document.getDocumentId())
                .signerId(signer.getExternalSignerId())
                .externalId(externalDocumentId)
                .name(documentName)
                .fileName(fileName)
                .size(fileSize)
                .hash(hash)
                .build();
    }

    private static int getFileSize(final MultipartFile file) {
        final var size = file.getSize();
        if (size > Integer.MAX_VALUE) {
            throw new DocumentUploadException("File is too large. Size: " + size);
        }
        return (int) size;
    }

    private static byte[] getFileBytes(final MultipartFile file) {
        try {
            return file.getBytes();
        } catch (final IOException e) {
            logger.warn("Exception when reading upload file", e);
            throw new DocumentUploadException("Failed to read file: " + e.getMessage(), e);
        }
    }

    private static String computeHash(final byte[] content, final DigestAlgorithm hashAlgorithm) {
        try {
            final var digest = hashAlgorithm.getMessageDigest();
            final var hashBytes = digest.digest(content);
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (final NoSuchAlgorithmException e) {
            logger.error("Hash algorithm not found: {}", hashAlgorithm, e);
            throw new DocumentUploadException("Hash algorithm not found: " + hashAlgorithm, e);
        }
    }

    /**
     * Signs the {@link Document} identified by {@code documentId} if it is in the correct state and signature is valid.
     *
     * The document is in correct state if:
     * <ul>
     *  <li> linked {@link Signer} is in {@link SignerStatus#ACTIVE} status </li>
     *  <li> the{@link Document} status is {@link DocumentStatus#WAITING} </li>
     *  <li> attempt to sign the document is within the configured waiting timeout (if configured) </li>
     * </ul>
     * Successful signing updates the {@link Document} status to {@link DocumentStatus#SIGNED} and stores the signed PDF document
     * into {@link DocumentContent} (It overrides the original unsigned PDF content with the signed one.).
     *
     * @param documentUuid identifier of the document to be signed
     * @param requestBody request body containing the signature
     * @return response with signed document details
     */
    SignDocumentResponse signDocument(final String documentUuid, final SignDocumentRequest requestBody) {
        final var document = documentRepository.findByDocumentId(documentUuid)
                .orElseThrow(() -> new DocumentNotFoundException(documentUuid));

        final var documentContent = documentContentRepository.findById(document.getDocumentContent())
                .orElseThrow(() -> new DocumentContentNotFoundException(documentUuid));

        final var signerReference = document.getSigner();
        final var signer = signerRepository.findById(signerReference)
                .orElseThrow(() -> new SignerNotFoundException(signerReference.getId()));

        verifyDocumentCanBeSigned(signer, document);

        final var signature = requestBody.signature();
        final var signedDocumentBytes = verifySignatureAndSignDocument(signer,
                document.getHash(),
                signature,
                documentContent.getContent(),
                configurationProperties.getSignatureHashAlgorithm());

        final var updatedDocumentContent = documentContent.toBuilder()
                .content(signedDocumentBytes)
                .build();

        documentContentRepository.save(updatedDocumentContent);

        final var updatedDocument = document.toBuilder()
                .timestampLastUpdated(Instant.now())
                .fileSize(signedDocumentBytes.length)
                .status(DocumentStatus.SIGNED)
                .signature(signature)
                .build();

        documentRepository.save(updatedDocument);

        final var downloadUrl = buildDocumentDownloadUri(documentUuid);
        return new SignDocumentResponse(documentUuid, downloadUrl);
    }

    private void verifyDocumentCanBeSigned(final Signer signer, final Document document) {
        if (signer.getStatus() != SignerStatus.ACTIVE) {
            throw new SignerStateException("Signer is not active. Signer: " + signer.getExternalSignerId());
        }

        if (document.getStatus() != DocumentStatus.WAITING) {
            throw new DocumentStateException("Document is not in state when it can be signed");
        }

        final var waitingTimeout = configurationProperties.getWaiting().getTimeout();
        if (waitingTimeout != null) {
            final var documentSigningDeadline = document.getTimestampCreated().plus(waitingTimeout);
            if (Instant.now().isAfter(documentSigningDeadline)) {
                throw new DocumentStateException("Document signing timeout exceeded");
            }
        }
    }

    private byte[] verifySignatureAndSignDocument(
            final Signer signer,
            final String hashBase64,
            final String hashSignatureBase64,
            final byte[] documentBytes,
            final DigestAlgorithm signatureAlgorithm) {

        final var certificate = convertCertificate(signer);
        final var certificateChain = convertCertificateChain(signer);

        final var signatureParams = createSignatureParameters(certificate, signatureAlgorithm, certificateChain);

        final var hashBytes = Base64.getDecoder().decode(hashBase64);
        final var hash = new ToBeSigned(hashBytes);

        final var signatureBytes = Base64.getDecoder().decode(hashSignatureBase64);
        final var signatureValue = new SignatureValue(signatureParams.getSignatureAlgorithm(), signatureBytes);

        final var isSignatureValid = padesService.isValidSignatureValue(hash, signatureValue, certificate);
        if (!isSignatureValid) {
            throw new DocumentInvalidSignatureException("Invalid signature");
        }

        final var unsignedDocument = new InMemoryDocument(documentBytes);
        final var signedDocument = padesService.signDocument(unsignedDocument, signatureParams, signatureValue);

        return readSignedDocumentBytes(signedDocument);
    }

    private static CertificateToken convertCertificate(final Signer signer) {
        try {
            final var x509Certificate = signer.getX509Certificate();
            return new CertificateToken(x509Certificate);
        } catch (final CertificateException e) {
            logger.warn("Exception when processing certificate", e);
            throw new CertificateProcessingException("Exception when processing certificate: " + e.getMessage(), e);
        }
    }

    private static List<CertificateToken> convertCertificateChain(final Signer signer) {
        try {
            final var chain = new ArrayList<CertificateToken>();

            for (final var base64Certificate : signer.getCertificateChain()) {
                final var x509Certificate = CertificateUtils.base64ToX509Certificate(base64Certificate);
                chain.add(new CertificateToken(x509Certificate));
            }

            return chain;
        } catch (final CertificateException e) {
            logger.warn("Exception when processing certificate chain", e);
            throw new CertificateProcessingException("Exception when processing certificate chain: " + e.getMessage(), e);
        }
    }

    private static PAdESSignatureParameters createSignatureParameters(
            final CertificateToken certificateToken,
            final DigestAlgorithm algorithm,
            final List<CertificateToken> certificateChain
    ) {
        final var params = new PAdESSignatureParameters();
        params.setDigestAlgorithm(algorithm);
        params.setSignatureLevel(SignatureLevel.PAdES_BASELINE_B);
        params.setSigningCertificate(certificateToken);
        params.setCertificateChain(certificateChain);

        return params;
    }

    private static byte[] readSignedDocumentBytes(final DSSDocument signedDocument) {
        try (final var stream = signedDocument.openStream()) {
            return stream.readAllBytes();
        } catch (final IOException e) {
            logger.warn("Exception when reading bytes of signed document", e);
            throw new DocumentSigningException("Failed to read signed document: " + e.getMessage(), e);
        }
    }

    private String buildDocumentDownloadUri(final String documentId) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(DOCUMENT_DOWNLOAD_PATH)
                .buildAndExpand(documentId)
                .toUriString();
    }

    /**
     * Downloads the content of the {@link Document} identified by {@code documentUuid}.
     *
     * @param documentUuid identifier of the document to be downloaded
     * @return document as a {@link Resource}
     */
    Resource downloadDocument(final String documentUuid) {
        final var document = documentRepository.findByDocumentId(documentUuid)
                .orElseThrow(() -> new DocumentNotFoundException(documentUuid));

        final var documentContent = documentContentRepository.findById(document.getDocumentContent())
                .orElseThrow(() -> new DocumentContentNotFoundException(documentUuid));

        if (document.getStatus() != DocumentStatus.SIGNED) {
            throw new DocumentStateException("Document is not signed yet");
        }

        return new ByteArrayResource(documentContent.getContent());
    }

    /**
     * Rejects the {@link Document} identified by {@code documentId}.
     *
     * @param documentUuid identifier of the document to be rejected
     * @param requestBody request body with the status {@link DocumentStatus#REJECTED}
     * @return response with rejected document details
     */
    RejectDocumentResponse rejectDocument(final String documentUuid, final RejectDocumentRequest requestBody) {
        final var requestedStatus = requestBody.status();
        if (requestedStatus != DocumentStatus.REJECTED) {
            throw new DocumentStatusTransitionException("Invalid status in the request body. Expected: %s, actual: %s".formatted(
                    DocumentStatus.REJECTED,
                    requestedStatus)
            );
        }

        final var document = documentRepository.findByDocumentId(documentUuid)
                .orElseThrow(() -> new DocumentNotFoundException(documentUuid));

        final var updatedDocument = document.toBuilder()
                .timestampLastUpdated(Instant.now())
                .status(DocumentStatus.REJECTED)
                .build();

        documentRepository.save(updatedDocument);

        return new RejectDocumentResponse(
                documentUuid,
                document.getDocumentName(),
                document.getFileName(),
                document.getFileSize(),
                document.getHash()
        );
    }

    /**
     * Deletes the {@link Document} and its {@link DocumentContent}.
     *
     * @param documentUuid identifier of the document to be deleted
     */
    void deleteDocument(final String documentUuid) {
        final var documentOpt = documentRepository.findByDocumentId(documentUuid);

        if (documentOpt.isEmpty()) {
            logger.warn("Document for deletion not found. Document UUID: {}", documentUuid);
            return;
        }

        final var document = documentOpt.get();
        documentRepository.delete(document);
        documentContentRepository.deleteById(document.getDocumentContent());
    }

    @Builder
    record CleanupResult(String rejectedDocuments, String signedDocuments, String waitingDocuments) {
    }
}
