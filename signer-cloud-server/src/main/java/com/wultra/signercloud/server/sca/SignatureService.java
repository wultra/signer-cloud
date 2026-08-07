/*
 * Signed Cloud
 * Copyright (C) 2026 Wultra s.r.o.
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
package com.wultra.signercloud.server.sca;

import com.wultra.signercloud.server.document.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import static net.logstash.logback.argument.StructuredArguments.kv;

/**
 * TODO
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Service
@AllArgsConstructor
@Slf4j
public class SignatureService {
    private static final Duration AUTHORIZATION_VALIDITY = Duration.ofMinutes(15);
    private static final long MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024;
    private static final String DOCUMENT_DOWNLOAD_PATH = "/signature/{documentId}/file";

    private final DocumentSigningService documentSigningService;
    private final DocumentContentRepository documentContentRepository;
    private final SigningTransactionRepository signingTransactionRepository;
    private final ObjectMapper objectMapper;
    private final ScaConfigProperties scaConfigProperties;
    private final CertificateService certificateService;

    @Transactional
    public CreateSignatureResponse createSignatureRequest(final CreateSignatureRequest request, final MultipartFile file) {
        validateFile(file);

        final var now = Instant.now();
        final var fileContent = getFileBytes(file);
        final var fileName = getFileName(file);

        final var authorizationExpiresAt = now.plus(AUTHORIZATION_VALIDITY);

        final DocumentContent savedContent = documentContentRepository.save(
                DocumentContent.builder()
                        .content(fileContent)
                        .build()
        );

        final SigningTransactionEntity transaction =
                SigningTransactionEntity.builder()
                        .id(UUID.randomUUID().toString())
                        .externalId(request.externalId())
                        .documentName(request.name())
                        .fileName(fileName)
                        .fileSize(file.getSize())
                        .documentContent(
                                AggregateReference.to(savedContent.getId())
                        )
                        .visualSignatureJson(
                                serializeVisualSignature(
                                        request.visualSignature()
                                )
                        )
                        .authorizationExpiresAt(
                                authorizationExpiresAt
                        )
                        .status(SigningTransactionStatus.AWAITING_USER_AUTHORIZATION)
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

        final SigningTransactionEntity savedTransaction = signingTransactionRepository.save(transaction);

        logger.info(
                "Signing transaction created",
                kv("action", "createSignatureRequest"),
                kv("state", "succeeded"),
                kv(
                        "signingTransactionId",
                        savedTransaction.getId()
                ),
                kv(
                        "documentExternalId",
                        savedTransaction.getExternalId()
                ),
                kv("status", savedTransaction.getStatus())
        );

        final String redirectUri = UriComponentsBuilder
                .fromUriString(scaConfigProperties.getScaBaseUrl())
                .path("/signature/callback")
                .build()
                .toUriString();

        final String authorizationUrl = UriComponentsBuilder
                .fromUriString(scaConfigProperties.getQtspBaseUrl())
                .path("/index.html")
                .queryParam("redirectUri", "{redirectUri}")
                .queryParam("requestId", "{requestId}")
                .encode(StandardCharsets.UTF_8)
                .buildAndExpand(
                        redirectUri,
                        savedTransaction.getId()
                )
                .toUriString();

        return new CreateSignatureResponse(
                savedTransaction.getId(),
                savedTransaction.getExternalId(),
                savedTransaction.getStatus(),
                savedTransaction.getAuthorizationExpiresAt(),
                authorizationUrl
        );
    }

    private void validateFile(final MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DocumentUploadException(
                    "A non-empty PDF document is required"
            );
        }

        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new DocumentUploadException(
                    "The PDF exceeds the maximum size of "
                            + MAX_FILE_SIZE_BYTES
                            + " bytes"
            );
        }

        if (!MediaType.APPLICATION_PDF_VALUE.equalsIgnoreCase(
                file.getContentType()
        )) {
            throw new DocumentUploadException(
                    "Unsupported content type: "
                            + file.getContentType()
            );
        }
    }

    private static String getFileName(
            final MultipartFile file
    ) {
        final String originalFilename =
                file.getOriginalFilename();

        if (originalFilename == null
                || originalFilename.isBlank()) {
            return "document.pdf";
        }

        /*
         * Avoid storing a path supplied by the client.
         */
        return originalFilename
                .replace("\\", "/")
                .substring(
                        originalFilename
                                .replace("\\", "/")
                                .lastIndexOf('/') + 1
                );
    }

    private static byte[] getFileBytes(
            final MultipartFile file
    ) {
        try {
            return file.getBytes();
        } catch (final IOException exception) {
            throw new DocumentUploadException(
                    "Could not read the uploaded PDF",
                    exception
            );
        }
    }

    private String serializeVisualSignature(
            final DocumentVisualSignature visualSignature
    ) {
        if (visualSignature == null) {
            return null;
        }

        return serializeJson(visualSignature);
    }

    private String serializeCertificateChain(
            final List<String> certificateChain
    ) {
        return serializeJson(certificateChain);
    }

    private List<X509Certificate> deserializeCertificateChain(
            final String certificateChainJson
    ) {
        if (certificateChainJson == null
                || certificateChainJson.isBlank()) {
            return List.of();
        }

        try {
            final String[] certificateChainBase64 =
                    objectMapper.readValue(
                            certificateChainJson,
                            String[].class
                    );

            return java.util.Arrays.stream(certificateChainBase64)
                    .map(SignatureService::decodeCertificate)
                    .toList();
        } catch (final JacksonException exception) {
            throw new IllegalStateException(
                    "Could not deserialize certificate chain",
                    exception
            );
        }
    }

    private String serializeJson(final Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (final JacksonException e) {
            throw new IllegalStateException(
                    "Could not serialize signing transaction data",
                    e
            );
        }
    }

    private static X509Certificate decodeCertificate(
            final String certificateBase64
    ) {
        try {
            final byte[] certificateBytes =
                    Base64.getDecoder().decode(
                            certificateBase64.replaceAll("\\s", "")
                    );

            final CertificateFactory factory =
                    CertificateFactory.getInstance("X.509");

            return (X509Certificate) factory.generateCertificate(
                    new ByteArrayInputStream(certificateBytes)
            );
        } catch (final IllegalArgumentException
                       | CertificateException exception) {
            throw new IllegalStateException(
                    "Could not decode the dummy QTSP certificate",
                    exception
            );
        }
    }

    private static byte[] sha256(final byte[] value) {
        try {
            return MessageDigest
                    .getInstance("SHA-256")
                    .digest(value);
        } catch (final NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    @Transactional
    public SignedDocumentResponse completeSignature(final String requestId, final String callbackData) {
        final SigningTransactionEntity transaction = signingTransactionRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Signing transaction was not found"));

        final var downloadUri = buildDocumentDownloadUri(requestId);

        /*
         * Makes a repeated callback idempotent after the transaction
         * has already completed.
         */
        if (transaction.getStatus() == SigningTransactionStatus.COMPLETED) {
            return new SignedDocumentResponse(downloadUri);
        }

        final var now = Instant.now();

//        if (!transaction.getAuthorizationExpiresAt().isAfter(now)) {
//            signingTransactionRepository.save(
//                    transaction.toBuilder()
//                            .status(SigningTransactionStatus.EXPIRED)
//                            .updatedAt(now)
//                            .build()
//            );
//
//            throw new IllegalStateException(
//                    "Signing transaction has expired"
//            );
//        }

        if (transaction.getStatus() != SigningTransactionStatus.AWAITING_USER_AUTHORIZATION) {
            throw new IllegalStateException("Signing transaction cannot be completed from status: " + transaction.getStatus());
        }

        final var originalDocumentId = transaction.getDocumentContent().getId();
        final var originalDocumentContent = documentContentRepository.findById(originalDocumentId)
                .orElseThrow(() -> new IllegalStateException("Original document content was not found"));

        final var pid = parsePID(callbackData);

        final var certificateAndKey = certificateService.generateCertificate(requestId, pid);

        final var certificate = certificateAndKey.certificate();
        final var privateKey = certificateAndKey.privateKey();

        final var toBeSigned = documentSigningService.computeToBeSigned(
                originalDocumentContent.getContent(),
                certificate,
                List.of(),
                now,
                null // TODO: parse visual signature
        );

        final var signature = signToBeSigned(toBeSigned, privateKey);

        final String signatureBase64 =
                Base64.getEncoder()
                        .encodeToString(signature);

        logger.info(
                "Completing signing transaction",
                kv("action", "completeSignature"),
                kv("state", "initiated"),
                kv("signingTransactionId", transaction.getId())
        );

        final var signedPdf = documentSigningService.sign(
                certificate,
                List.of(),
                signatureBase64,
                originalDocumentContent.getContent(),
                now,
                null,
                null
        );

        final DocumentContent savedSignedDocumentContent =
                documentContentRepository.save(
                        DocumentContent.builder()
                                .content(signedPdf.content())
                                .build()
                );

        final SigningTransactionEntity completedTransaction =
                transaction.toBuilder()
                        .signedDocumentContent(
                                AggregateReference.to(
                                        savedSignedDocumentContent.getId()
                                )
                        )
                        .status(SigningTransactionStatus.COMPLETED)
                        .completedAt(now)
                        .updatedAt(now)
                        // todo: serialize cert + chain
                        // toBeSigned
                        .build();

        signingTransactionRepository.save(completedTransaction);

        logger.info(
                "Signing transaction completed",
                kv("action", "completeSignature"),
                kv("state", "succeeded"),
                kv(
                        "signingTransactionId",
                        completedTransaction.getId()
                ),
                kv("status", completedTransaction.getStatus())
        );

        return new SignedDocumentResponse(downloadUri);
    }

    private String buildDocumentDownloadUri(final String documentId) {
        return ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(DOCUMENT_DOWNLOAD_PATH)
                .buildAndExpand(documentId)
                .toUriString();
    }

    private static byte[] signToBeSigned(
            final byte[] toBeSigned,
            final PrivateKey privateKey
    ) {
        if (toBeSigned == null || toBeSigned.length == 0) {
            throw new IllegalArgumentException(
                    "ToBeSigned must not be empty"
            );
        }

        if (privateKey == null) {
            throw new IllegalArgumentException(
                    "Private key is required"
            );
        }

        if (!"EC".equalsIgnoreCase(privateKey.getAlgorithm())) {
            throw new IllegalArgumentException(
                    "Expected EC private key, got: "
                            + privateKey.getAlgorithm()
            );
        }

        try {
            final Signature signature =
                    Signature.getInstance(
                            "SHA256withECDSA",
                            BouncyCastleProvider.PROVIDER_NAME
                    );

            signature.initSign(privateKey);
            signature.update(toBeSigned);

            return signature.sign();

        } catch (final GeneralSecurityException exception) {
            throw new IllegalStateException(
                    "Could not create ECDSA/SHA-256 signature",
                    exception
            );
        }
    }

    Resource downloadDocument(final String transactionUuid) {
        final var document = signingTransactionRepository.findById(transactionUuid)
                .orElseThrow(() -> DocumentNotFoundException.forId(transactionUuid));

        final var documentContent = documentContentRepository.findById(document.getSignedDocumentContent())
                .orElseThrow(() -> DocumentContentNotFoundException.forId(transactionUuid));

        final var status = document.getStatus();
        if (status != SigningTransactionStatus.COMPLETED) {
            final var errorMessage = resolveDownloadErrorMessage(status);
            throw new DocumentStateException(errorMessage);
        }

        return new ByteArrayResource(documentContent.getContent());
    }

    private String resolveDownloadErrorMessage(final SigningTransactionStatus status) {
        return switch (status) {
            case AWAITING_USER_AUTHORIZATION -> "Document is not signed yet";
            case FAILED -> "Document signing failed";
            case EXPIRED -> "Document signing expired";
            default -> "Unknown document state";
        };
    }

    private PID parsePID(final String callbackData) {
        final byte[] decoded = Base64.getDecoder().decode(callbackData);
        final String json = new String(decoded, StandardCharsets.UTF_8);

        final JsonNode root = objectMapper.readTree(json);
        final JsonNode pid = root
                .path("translatedPayload")
                .path("pidTranslated");

        if (pid.isMissingNode() || pid.isNull()) {
            throw new IllegalArgumentException("Callback data does not contain translatedPayload.pidTranslated");
        }

        return new PID(
                requiredText(pid, "given_name"),
                requiredText(pid, "familyName"),
                requiredText(pid, "birthdate")
        );
    }

    private static String requiredText(final JsonNode node, final String field) {
        final JsonNode value = node.get(field);

        if (value == null || value.isNull() || !value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException("Missing or invalid PID field: " + field);
        }

        return value.asString();
    }
}
