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

import com.wultra.signercloud.server.configuration.PAdESConfigurationProperties;
import com.wultra.signercloud.server.signer.*;
import com.wultra.signercloud.server.utils.CertificateUtils;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.SignatureImageParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.pdf.AnnotationBox;
import eu.europa.esig.dss.pdf.PdfSignatureFieldPositionChecker;
import eu.europa.esig.dss.pdf.pdfbox.PdfBoxDocumentReader;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.core5.http.ContentType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
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

    private final DocumentConfigurationProperties documentConfigurationProperties;
    private final DocumentRepository documentRepository;
    private final DocumentContentRepository documentContentRepository;
    private final SignerRepository signerRepository;
    private final PAdESService padesService;
    private final PAdESConfigurationProperties pAdESConfigurationProperties;
    private final DocumentVisualSignatureService documentVisualSignatureService;
    private final PdfSignatureFieldPositionChecker visualSignatureChecker;

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
                documentConfigurationProperties.getRejected().getRetentionPeriod(),
                cleanupResultBuilder::rejectedDocuments,
                now
        );

        processRetention(
                DocumentStatus.SIGNED,
                documentConfigurationProperties.getSigned().getRetentionPeriod(),
                cleanupResultBuilder::signedDocuments,
                now
        );

        processRetention(
                DocumentStatus.WAITING,
                documentConfigurationProperties.getWaiting().getRetentionPeriod(),
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
            final MultipartFile file,
            final DocumentVisualSignature visualSignature
    ) {
        final var contentType = file.getContentType();
        if (!ContentType.APPLICATION_PDF.getMimeType().equals(contentType)) {
            throw new DocumentUploadException("Unsupported content type: " + contentType);
        }

        final var signer = signerRepository.findByExternalSignerId(externalSignerId)
                .orElseThrow(() -> SignerNotFoundException.forId(externalSignerId));

        final var timestampCreated = Instant.now();

        final var fileName = file.getOriginalFilename();
        final var fileSize = getFileSize(file);
        final var fileContent = getFileBytes(file);
        final var hash = computeHash(fileContent, signer, timestampCreated, visualSignature);

        final var documentContent = DocumentContent.builder()
                .content(fileContent)
                .build();

        final var savedDocumentContent = documentContentRepository.save(documentContent);

        final var document = Document.builder()
                .timestampCreated(timestampCreated)
                .documentId(UUID.randomUUID().toString())
                .externalId(externalDocumentId)
                .signer(AggregateReference.to(signer.getId()))
                .documentName(documentName)
                .fileName(fileName)
                .fileSize(fileSize)
                .documentContent(AggregateReference.to(savedDocumentContent.getId()))
                .hash(hash)
                .status(DocumentStatus.WAITING)
                .visualSignature(visualSignature)
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
            throw new DocumentUploadException("Exception when reading upload file: " + e.getMessage(), e);
        }
    }

    private String computeHash(
            final byte[] content,
            final Signer signer,
            final Instant timestampSigned,
            final DocumentVisualSignature visualSignature
    ) {
        final var certificate = convertCertificate(signer);
        final var certificateChain = convertCertificateChain(signer);

        final var dssDocument = new InMemoryDocument(content);

        final var signatureParams = createSignatureParameters(
                certificate,
                certificateChain,
                timestampSigned,
                pAdESConfigurationProperties.getSignatureLevel(),
                visualSignature,
                dssDocument
        );

        Optional.ofNullable(signatureParams.getImageParameters())
                .ifPresent(imageParams -> verifyVisualSignature(dssDocument, imageParams));

        final var toSignBytes = padesService.getDataToSign(dssDocument, signatureParams);

        return Base64.getEncoder().encodeToString(toSignBytes.getBytes());
    }

    private void verifyVisualSignature(
            final DSSDocument dssDocument,
            final SignatureImageParameters signatureImageParameters
    ) {
        try (final var pdfReader = new PdfBoxDocumentReader(dssDocument)) {
            final var fieldParams = signatureImageParameters.getFieldParameters();
            visualSignatureChecker.assertSignatureFieldPositionValid(pdfReader, new AnnotationBox(fieldParams), fieldParams.getPage());
        } catch (final IOException | RuntimeException e) {
            throw new DocumentVisualSignatureException(e.getMessage(), e);
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
                .orElseThrow(() -> DocumentNotFoundException.forId(documentUuid));

        final var documentContent = documentContentRepository.findById(document.getDocumentContent())
                .orElseThrow(() -> DocumentContentNotFoundException.forId(documentUuid));

        final var signerReference = document.getSigner();
        final var signer = signerRepository.findById(signerReference)
                .orElseThrow(() -> SignerNotFoundException.forInternalId(signerReference.getId()));

        verifyDocumentCanBeSigned(signer, document);

        final var signature = requestBody.signature();
        final var signatureLevel = resolveDocumentSignatureLevel(requestBody.signatureLevel());
        final var signedDocumentBytes = verifySignatureAndSignDocument(
                signer,
                signature,
                documentContent.getContent(),
                document.getTimestampCreated(),
                signatureLevel,
                document.getVisualSignature());

        final var updatedDocumentContent = documentContent.toBuilder()
                .content(signedDocumentBytes)
                .build();

        documentContentRepository.save(updatedDocumentContent);

        final var updatedDocument = document.toBuilder()
                .timestampLastUpdated(Instant.now())
                .fileSize(signedDocumentBytes.length)
                .status(DocumentStatus.SIGNED)
                .signature(signature)
                .signatureLevel(signatureLevel)
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

        final var waitingTimeout = documentConfigurationProperties.getWaiting().getTimeout();
        if (waitingTimeout != null) {
            final var documentSigningDeadline = document.getTimestampCreated().plus(waitingTimeout);
            if (Instant.now().isAfter(documentSigningDeadline)) {
                throw new DocumentStateException("Document signing timeout exceeded");
            }
        }
    }

    private DocumentSignatureLevel resolveDocumentSignatureLevel(final DocumentSignatureLevel requestedSignatureLevel) {
        final var signatureLevel = Optional.ofNullable(requestedSignatureLevel)
                .orElse(pAdESConfigurationProperties.getSignatureLevel());

        if (signatureLevel == DocumentSignatureLevel.PADES_B_T && StringUtils.isEmpty(pAdESConfigurationProperties.getTsaUrl())) {
            throw new TimestampAuthorityException("TSA URL not set in configuration");
        }

        return signatureLevel;
    }

    private byte[] verifySignatureAndSignDocument(
            final Signer signer,
            final String hashSignatureBase64,
            final byte[] documentBytes,
            final Instant timestampSigned,
            final DocumentSignatureLevel signatureLevel,
            final DocumentVisualSignature visualSignature) {

        final var certificate = convertCertificate(signer);
        final var certificateChain = convertCertificateChain(signer);

        final var unsignedDocument = new InMemoryDocument(documentBytes);

        final var signatureParams = createSignatureParameters(
                certificate,
                certificateChain,
                timestampSigned,
                signatureLevel,
                visualSignature,
                unsignedDocument
        );

        final var documentHash = padesService.getDataToSign(unsignedDocument, signatureParams);

        final var signatureBytes = Base64.getDecoder().decode(hashSignatureBase64);
        final var signatureValue = new SignatureValue(signatureParams.getSignatureAlgorithm(), signatureBytes);

        final var isSignatureValid = padesService.isValidSignatureValue(documentHash, signatureValue, certificate);
        if (!isSignatureValid) {
            throw new DocumentInvalidSignatureException("Invalid signature");
        }

        final var signedDocument = padesService.signDocument(unsignedDocument, signatureParams, signatureValue);

        return readSignedDocumentBytes(signedDocument);
    }

    private static CertificateToken convertCertificate(final Signer signer) {
        try {
            final var x509Certificate = signer.getX509Certificate();
            return new CertificateToken(x509Certificate);
        } catch (final CertificateException e) {
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
            throw new CertificateProcessingException("Exception when processing certificate chain: " + e.getMessage(), e);
        }
    }

    /**
     * Creates signature parameters with a deterministic context.
     *
     * This method always creates a new instance. These parameters affect the {@link eu.europa.esig.dss.model.ToBeSigned}
     * value returned by {@link PAdESService#getDataToSign(DSSDocument, PAdESSignatureParameters)}
     * and computed internally in {@link PAdESService#signDocument(DSSDocument, PAdESSignatureParameters, SignatureValue)}.
     * Therefore, it is important that the same context is used for the same signature, so that {@link eu.europa.esig.dss.model.ToBeSigned}
     * is computed deterministically (i.e., the value is always the same).
     * Pay attention to the {@link eu.europa.esig.dss.model.BLevelParameters#setSigningDate(Date)} in {@link PAdESSignatureParameters#bLevel}. From this value, the
     * {@code deterministicId} in {@link PAdESSignatureParameters#getContext()} is computed.
     * If {@code signingDate} is not explicitly set, the current machine time is used by default, and each call
     * to obtain {@link eu.europa.esig.dss.model.ToBeSigned} will produce a different value.
     * A list of signed parameters is available <a href="https://ec.europa.eu/digital-building-blocks/DSS/webapp-demo/doc/dss-documentation.html#_table_with_all_attributes_per_format_and_class">here</a>.
     *
     * @param certificateToken signature certificate
     * @param certificateChain signature certificate chain
     * @param timestampSigned timestamp in UTC set as {@code signingDate} in order to create deterministic context
     * @return parameters with deterministic context
     */
    private PAdESSignatureParameters createSignatureParameters(
            final CertificateToken certificateToken,
            final List<CertificateToken> certificateChain,
            final Instant timestampSigned,
            final DocumentSignatureLevel documentSignatureLevel,
            final DocumentVisualSignature visualSignature,
            final DSSDocument dssDocument
    ) {
        final var params = new PAdESSignatureParameters();
        params.setDigestAlgorithm(pAdESConfigurationProperties.getHashAlgorithm());
        params.setSigningCertificate(certificateToken);
        params.setCertificateChain(certificateChain);

        final var signatureLevel = switch (documentSignatureLevel) {
            case PADES_B_B -> SignatureLevel.PAdES_BASELINE_B;
            case PADES_B_T -> SignatureLevel.PAdES_BASELINE_T;
        };

        params.setSignatureLevel(signatureLevel);

        Optional.ofNullable(visualSignature)
                .map(vs -> documentVisualSignatureService.createVisualSignature(vs, dssDocument))
                .ifPresent(params::setImageParameters);

        params.bLevel().setSigningDate(Date.from(timestampSigned));
        params.setSigningTimeZone(TimeZone.getTimeZone("UTC"));

        return params;
    }

    private static byte[] readSignedDocumentBytes(final DSSDocument signedDocument) {
        try (final var stream = signedDocument.openStream()) {
            return stream.readAllBytes();
        } catch (final IOException e) {
            throw new DocumentSigningException("Exception when reading bytes of signed document: " + e.getMessage(), e);
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
                .orElseThrow(() -> DocumentNotFoundException.forId(documentUuid));

        final var documentContent = documentContentRepository.findById(document.getDocumentContent())
                .orElseThrow(() -> DocumentContentNotFoundException.forId(documentUuid));

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
                .orElseThrow(() -> DocumentNotFoundException.forId(documentUuid));

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
