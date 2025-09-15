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

import com.wultra.signercloud.server.restapi.Try;
import com.wultra.signercloud.server.signer.Signer;
import com.wultra.signercloud.server.signer.SignerNotFoundException;
import com.wultra.signercloud.server.signer.SignerRepository;
import com.wultra.signercloud.server.signer.SignerStatus;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
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

    private static final String DOCUMENT_DOWNLOAD_PATH = "/api/v1/documents/{documentId}/download";

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
            long deletedCount = documentRepository.deleteByStatusAndTimestampCreatedBefore(status, now.minus(retentionPeriod));
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
     * @return response as a {@link Try}
     */
    Try<UploadDocumentResponse> uploadDocument(
            final String externalSignerId,
            final String externalDocumentId,
            final String documentName,
            final MultipartFile file
    ) throws NoSuchAlgorithmException {
       try {
           final var response = processDocumentUpload(externalSignerId, externalDocumentId, documentName, file);
           return Try.success(response);
       } catch (final SignerNotFoundException | DocumentUploadException e) {
           return Try.error(e);
       }
    }

    private UploadDocumentResponse processDocumentUpload(
            final String externalSignerId,
            final String externalDocumentId,
            final String documentName,
            final MultipartFile file
    ) throws NoSuchAlgorithmException {
        final var contentType = file.getContentType();
        if (!ContentType.APPLICATION_PDF.getMimeType().equals(contentType)) {
            throw new DocumentUploadException("Unsupported content type: " + contentType);
        }

        final var signer = signerRepository.findByExternalSignerId(externalSignerId)
                .orElseThrow(() -> new SignerNotFoundException("Signer not found for external signer ID: " + externalSignerId));

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
                .signerId(signer.getId())
                .documentName(documentName)
                .fileName(fileName)
                .fileSize(fileSize)
                .documentContentId(savedDocumentContent.getId())
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
            throw new DocumentUploadException("Failed to read file: " + e.getMessage());
        }
    }

    private static String computeHash(final byte[] content, final DigestAlgorithm hashAlgorithm) throws NoSuchAlgorithmException {
        try {
            final var digest = hashAlgorithm.getMessageDigest();
            final var hashBytes = digest.digest(content);
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (final NoSuchAlgorithmException e) {
            logger.error("Hash algorithm not found: {}", hashAlgorithm, e);
            throw e;
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
     * @param documentId identifier of the document to be signed
     * @param requestBody request body containing the signature
     * @return response as a {@link Try}
     */
    Try<SignDocumentResponse> signDocument(final String documentId, final SignDocumentRequest requestBody) {
        try {
            final var response = processSignDocument(documentId, requestBody);
            return Try.success(response);
        } catch (final CertificateException | DocumentNotFoundException | SignerNotFoundException | SignDocumentException e) {
            return Try.error(e);
        }
    }

    private SignDocumentResponse processSignDocument(final String documentId, final SignDocumentRequest requestBody) throws CertificateException {

        final var document = documentRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found for document ID: " + documentId));

        final var documentContent = documentContentRepository.findById(document.getDocumentContentId())
                .orElseThrow(() -> new DocumentNotFoundException("Document content not found for document ID: " + documentId));

        final var signer = signerRepository.findById(document.getSignerId())
                .orElseThrow(() -> new SignerNotFoundException("Signer not found for document ID: " + documentId));

        verifyDocumentCanBeSigned(signer, document);

        final var signature = requestBody.signature();
        final var signedDocumentBytes = verifySignatureAndSignDocument(signer.getX509Certificate(),
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

        final var downloadUrl = buildDocumentDownloadUri(documentId);
        return new SignDocumentResponse(documentId, downloadUrl);
    }

    private void verifyDocumentCanBeSigned(final Signer signer, final Document document) {
        if (signer.getStatus() != SignerStatus.ACTIVE) {
            throw new SignDocumentException("Signer is not active. Signer: " + signer.getExternalSignerId());
        }

        if (document.getStatus() != DocumentStatus.WAITING) {
            throw new SignDocumentException("Document is not in state when it can be signed");
        }

        final var waitingTimeout = configurationProperties.getWaiting().getTimeout();
        if (waitingTimeout != null) {
            final var documentSigningDeadline = document.getTimestampCreated().plus(waitingTimeout);
            if (Instant.now().isAfter(documentSigningDeadline)) {
                throw new SignDocumentException("Document signing timeout exceeded");
            }
        }
    }

    private byte[] verifySignatureAndSignDocument(
            final X509Certificate x509Certificate,
            final String hashBase64,
            final String hashSignatureBase64,
            final byte[] documentBytes,
            final DigestAlgorithm signatureAlgorithm) {

        final var certificateToken = new CertificateToken(x509Certificate);
        final var signatureParams = createSignatureParameters(certificateToken, signatureAlgorithm);

        final var hashBytes = Base64.getDecoder().decode(hashBase64);
        final var hash = new ToBeSigned(hashBytes);

        final var signatureBytes = Base64.getDecoder().decode(hashSignatureBase64);
        final var signatureValue = new SignatureValue(signatureParams.getSignatureAlgorithm(), signatureBytes);

        final var isSignatureValid = padesService.isValidSignatureValue(hash, signatureValue, certificateToken);
        if (!isSignatureValid) {
            throw new SignDocumentException("Invalid signature");
        }

        final var unsignedDocument = new InMemoryDocument(documentBytes);
        final var signedDocument = padesService.signDocument(unsignedDocument, signatureParams, signatureValue);

        return readSignedDocumentBytes(signedDocument);
    }

    private static PAdESSignatureParameters createSignatureParameters(final CertificateToken certificateToken, final DigestAlgorithm algorithm) {
        final var params = new PAdESSignatureParameters();
        params.setDigestAlgorithm(algorithm);
        params.setSignatureLevel(SignatureLevel.PAdES_BASELINE_B);
        params.setSigningCertificate(certificateToken);
        //TODO (michalrozehnal, 02.09.2025): add setCertificateChain(...), make it configurable and set it by default

        return params;
    }

    private static byte[] readSignedDocumentBytes(final DSSDocument signedDocument) {
        try (final var stream = signedDocument.openStream()) {
            return stream.readAllBytes();
        } catch (final IOException e) {
            logger.error("Exception when reading bytes of signed document", e);
            throw new SignDocumentException("Failed to read signed document: " + e.getMessage());
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
     * @return response as a {@link Try}
     */
    Try<Resource> downloadDocument(final String documentUuid) {
        try {
            final var response = processDownloadDocument(documentUuid);
            return Try.success(response);
        } catch (final DocumentNotFoundException | DownloadDocumentException e) {
            return Try.error(e);
        }
    }

    private Resource processDownloadDocument(final String documentUuid) {
        final var document = documentRepository.findByDocumentId(documentUuid)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found for document ID: " + documentUuid));

        final var documentContent = documentContentRepository.findById(document.getDocumentContentId())
                .orElseThrow(() -> new DocumentNotFoundException("Document content not found for document ID: " + documentUuid));

        if (document.getStatus() != DocumentStatus.SIGNED) {
            throw new DownloadDocumentException("Document is not signed yet");
        }

        return new ByteArrayResource(documentContent.getContent());
    }

    /**
     * Rejects the {@link Document} identified by {@code documentId}.
     *
     * @param documentId identifier of the document to be rejected
     * @param requestBody request body with the status {@link DocumentStatus#REJECTED}
     * @return response as a {@link Try}
     */
    Try<RejectDocumentResponse> rejectDocument(final String documentId, final RejectDocumentRequest requestBody) {
        try {
            final var response = processRejectDocument(documentId, requestBody);
            return Try.success(response);
        } catch (final DocumentNotFoundException | RejectDocumentException e) {
            return Try.error(e);
        }
    }

    private RejectDocumentResponse processRejectDocument(final String documentId, final RejectDocumentRequest requestBody) {
        final var requestedStatus = requestBody.status();
        if (requestedStatus != DocumentStatus.REJECTED) {
            throw new RejectDocumentException("Invalid status in the request body. Expected: %s, actual: %s".formatted(
                    DocumentStatus.REJECTED,
                    requestedStatus)
            );
        }

        final var document = documentRepository.findByDocumentId(documentId)
                .orElseThrow(() -> new DocumentNotFoundException("Document not found for document ID: " + documentId));

        final var updatedDocument = document.toBuilder()
                .timestampLastUpdated(Instant.now())
                .status(DocumentStatus.REJECTED)
                .build();

        documentRepository.save(updatedDocument);

        return new RejectDocumentResponse(
                documentId,
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
        documentContentRepository.deleteById(document.getDocumentContentId());
    }

    @Builder
    record CleanupResult(String rejectedDocuments, String signedDocuments, String waitingDocuments) {
    }
}
