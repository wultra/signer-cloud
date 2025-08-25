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
import com.wultra.signercloud.server.signer.SignerNotFoundException;
import com.wultra.signercloud.server.signer.SignerRepository;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.core5.http.ContentType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
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
    private static final String HASH_ALGORITHM = "SHA-256";

    private final DocumentConfigurationProperties configurationProperties;

    private final DocumentRepository documentRepository;
    private final DocumentContentRepository documentContentRepository;
    private final SignerRepository signerRepository;

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
     * @param externalSignerId {@link com.wultra.signercloud.server.signer.Signer#externalSignerId}
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
    ) {
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
    ) {
        final var contentType = file.getContentType();
        if (!ContentType.APPLICATION_PDF.getMimeType().equals(contentType)) {
            throw new DocumentUploadException("Unsupported content type: " + contentType);
        }

        final var signer = signerRepository.findByExternalSignerId(externalSignerId)
                .orElseThrow(() -> new SignerNotFoundException("Signer not found for external signer ID: " + externalSignerId));

        final var fileName = file.getOriginalFilename();
        final var fileSize = getFileSize(file);
        final var fileContent = getFileBytes(file);
        final var hash = computeHash(fileContent);

        final var documentContent = DocumentContent.builder()
                .content(fileContent)
                .build();

        final var savedDocumentContent = documentContentRepository.save(documentContent);

        final var document = Document.builder()
                .timestampCreated(Instant.now())
                .documentId(externalSignerId)
                //.externalId(externalSignerId)
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
                .documentId(String.valueOf(document.getId()))
                .signerId(externalSignerId)
                .externalId(externalDocumentId)
                .name(documentName)
                .fileName(fileName)
                .size(fileSize)
                .hash(hash)
                .build();
    }

    private int getFileSize(final MultipartFile file) {
        final var size = file.getSize();
        if (size > Integer.MAX_VALUE) {
            throw new DocumentUploadException("File is too large. Size: " + size);
        }
        return (int) size;
    }

    private byte[] getFileBytes(final MultipartFile file) {
        try {
            return file.getBytes();
        } catch (final IOException e) {
            throw new DocumentUploadException("Failed to read file: " + e.getMessage());
        }
    }

    private String computeHash(final byte[] content) {
        try {
            final var digest = MessageDigest.getInstance(HASH_ALGORITHM);
            final var hashBytes = digest.digest(content);
            return HexFormat.of().formatHex(hashBytes);
        } catch (final NoSuchAlgorithmException e) {
            throw new DocumentUploadException("Failed to compute hash: " + e.getMessage());
        }
    }

    @Builder
    record CleanupResult(String rejectedDocuments, String signedDocuments, String waitingDocuments) {
    }
}
