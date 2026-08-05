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
package com.wultra.signercloud.server.qtsp;

import com.wultra.signercloud.server.document.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.digests.NullDigest;
import org.bouncycastle.crypto.signers.DSADigestSigner;
import org.bouncycastle.crypto.signers.ECDSASigner;
import org.bouncycastle.crypto.signers.StandardDSAEncoding;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
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
    private static final String ECDSA_SHA_256_OID ="1.2.840.10045.4.3.2";

    private static final String SHA_256_OID = "2.16.840.1.101.3.4.2.1";
    private static final Duration AUTHORIZATION_VALIDITY = Duration.ofMinutes(10);
    private static final long MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024;

    // TODO: put dummy into config?
    private static final URI DUMMY_AUTHORIZATION_ENDPOINT = URI.create("https://qtsp.example/oauth2/authorize");
    private static final String DUMMY_CLIENT_ID = "signature-creation-application";
    private static final String DUMMY_REDIRECT_URI = "https://sca.example/api/v1/oauth2/qtsp/callback";
    private static final String SIGNING_CERTIFICATE_BASE64 = """
        MIIB2jCCAX+gAwIBAgIUUa5jIofJUv+hj2LuiV3vfVTcl7YwCgYIKoZIzj0EAwIwQjELMAkGA1UEBhMCQ1oxEDAOBgNVBAoMB0V4YW1wbGUxITAfBgNVBAMMGFN0YXRpYyBRVFNQIEVDRFNBIFNpZ25lcjAeFw0yNjA4MDMxODU4MjZaFw0zNjA3MzExODU4MjZaMEIxCzAJBgNVBAYTAkNaMRAwDgYDVQQKDAdFeGFtcGxlMSEwHwYDVQQDDBhTdGF0aWMgUVRTUCBFQ0RTQSBTaWduZXIwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAQb4gd2M7wMo9XyUHaflcFjxDIS6dEF4w/zuHrwrOQiGuH95/2mbLw72tlxv8Tl3wsYWTk3EoZT8Tkjy/qJhCdho1MwUTAdBgNVHQ4EFgQUm/jWrctIfcW+nI+5VOSXqe9DWQ4wHwYDVR0jBBgwFoAUm/jWrctIfcW+nI+5VOSXqe9DWQ4wDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNJADBGAiEAg7CfXPzyTruLU2ZmLO/jrgCqtzJG7OZZ5OqdJ+bhancCIQDW/VO0Hg4xwo9qylex+rj8hv2+qtWbU7qTGuvDBX0B3Q==
        """;

    private static final String SIGNING_PRIVATE_KEY_PKCS8_BASE64 = """
        MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgIu0iwSU6xIE98HtQDOMlT7HfQ3QP4TKxAzSNoDMWlbGhRANCAAQb4gd2M7wMo9XyUHaflcFjxDIS6dEF4w/zuHrwrOQiGuH95/2mbLw72tlxv8Tl3wsYWTk3EoZT8Tkjy/qJhCdh
        """;

    private final DocumentSigningService documentSigningService;
    private final DocumentContentRepository documentContentRepository;
    private final SigningTransactionRepository signingTransactionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public CreateSignatureResponse createSignatureRequest(final CreateSignatureRequest request, final MultipartFile file) {
        validateFile(file);

        final var now = Instant.now();
        final var fileContent = getFileBytes(file);
        final var fileName = getFileName(file);

        final var credentialInfo = getCredentialInfoFromQtsp(
                request.qtspSessionId(),
                request.credentialId()
        );

        final var toBeSigned = documentSigningService.computeToBeSigned(
                fileContent,
                credentialInfo.certificate(),
                credentialInfo.certificateChain(),
                now,
                request.visualSignature()
        );

        final byte[] toBeSignedHash = sha256(toBeSigned);

        final String toBeSignedHashBase64 = Base64.getEncoder().encodeToString(toBeSignedHash);

        final var oauthState = randomBase64Url(32);
        final var pkceCodeVerifier = randomBase64Url(64);
        final var pkceCodeChallenge = createPkceCodeChallenge(pkceCodeVerifier);

        final var authorizationExpiresAt = now.plus(AUTHORIZATION_VALIDITY);

        final URI authorizationUrl = createAuthorizationUrl(
                request,
                toBeSignedHashBase64,
                oauthState,
                pkceCodeChallenge
        );

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
                        .qtspSessionId(request.qtspSessionId())
                        .credentialId(request.credentialId())
                        .certificateBase64(
                                credentialInfo.certificateBase64()
                        )
                        .certificateChainJson(
                                serializeCertificateChain(
                                        credentialInfo.certificateChainBase64()
                                )
                        )
                        .toBeSignedHashBase64(toBeSignedHashBase64)
                        .hashAlgorithmOid(SHA_256_OID)
                        .signAlgorithmOid(
                                credentialInfo.signAlgorithmOid()
                        )
                        .signatureDate(now)
                        .visualSignatureJson(
                                serializeVisualSignature(
                                        request.visualSignature()
                                )
                        )
                        .oauthState(oauthState)
                        .pkceCodeVerifier(pkceCodeVerifier)
                        .authorizationUrl(authorizationUrl.toString())
                        .authorizationExpiresAt(
                                authorizationExpiresAt
                        )
                        .status(
                                SigningTransactionStatus
                                        .AWAITING_USER_AUTHORIZATION
                        )
                        .createdAt(now)
                        .updatedAt(now)
                        .build();

        final SigningTransactionEntity savedTransaction =
                signingTransactionRepository.save(transaction);

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
                kv(
                        "credentialId",
                        savedTransaction.getCredentialId()
                ),
                kv("status", savedTransaction.getStatus())
        );

        return new CreateSignatureResponse(
                savedTransaction.getId(),
                savedTransaction.getExternalId(),
                savedTransaction.getStatus(),
                URI.create(savedTransaction.getAuthorizationUrl()),
                savedTransaction.getAuthorizationExpiresAt(),
                oauthState
        );
    }

    /**
     * Temporary replacement for a real QTSP credentials/info call.
     *
     * The arguments are intentionally accepted even though the method
     * currently returns the same static data for every request.
     */
    private QtspCredentialInfo getCredentialInfoFromQtsp(
            final String qtspSessionId,
            final String credentialId
    ) {
        /*
         * Dummy implementation for now.
         *
         * Later, replace this method with a real QTSP call to:
         * POST /csc/v2/credentials/info
         */

        logger.info(
                "QTSP credential information obtained",
                kv("action", "getCredentialInfo"),
                kv("qtspSessionId", qtspSessionId),
                kv("credentialId", credentialId)
        );

        final X509Certificate certificate =
                decodeCertificate(SIGNING_CERTIFICATE_BASE64);

        return new QtspCredentialInfo(
                credentialId,
                certificate,
                List.of(),
                SIGNING_CERTIFICATE_BASE64,
                List.of(),
                ECDSA_SHA_256_OID
        );
    }

    private URI createAuthorizationUrl(
            final CreateSignatureRequest request,
            final String toBeSignedHash,
            final String oauthState,
            final String pkceCodeChallenge
    ) {
        return UriComponentsBuilder
                .fromUri(DUMMY_AUTHORIZATION_ENDPOINT)
                .queryParam("response_type", "code")
                .queryParam("client_id", DUMMY_CLIENT_ID)
                .queryParam("redirect_uri", DUMMY_REDIRECT_URI)
                .queryParam("scope", "credential")
                .queryParam(
                        "credentialID",
                        request.credentialId()
                )
                .queryParam("numSignatures", 1)
                .queryParam("hashes", toBeSignedHash)
                .queryParam(
                        "hashAlgorithmOID",
                        SHA_256_OID
                )
                .queryParam(
                        "code_challenge",
                        pkceCodeChallenge
                )
                .queryParam(
                        "code_challenge_method",
                        "S256"
                )
                .queryParam("state", oauthState)
                .build()
                .encode()
                .toUri();
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

    private static String randomBase64Url(
            final int numberOfBytes
    ) {
        final byte[] randomBytes =
                new byte[numberOfBytes];

        new SecureRandom().nextBytes(randomBytes);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(randomBytes);
    }

    private static String createPkceCodeChallenge(
            final String codeVerifier
    ) {
        final byte[] digest = sha256(
                codeVerifier.getBytes(StandardCharsets.US_ASCII)
        );

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(digest);
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
    public String completeSignature(final String authorizationCode, final String state) {
        if (authorizationCode == null || authorizationCode.isBlank()) {
            throw new IllegalArgumentException("Authorization code is required");
        }

        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("OAuth state is required");
        }

        final SigningTransactionEntity transaction = signingTransactionRepository.findByOauthState(state)
                .orElseThrow(() -> new IllegalArgumentException("Signing transaction was not found"));

        /*
         * Makes a repeated callback idempotent after the transaction
         * has already completed.
         */
        if (transaction.getStatus() == SigningTransactionStatus.COMPLETED) {
            return transaction.getId();
        }

        final var now = Instant.now();

        if (!transaction.getAuthorizationExpiresAt().isAfter(now)) {
            signingTransactionRepository.save(
                    transaction.toBuilder()
                            .status(SigningTransactionStatus.EXPIRED)
                            .updatedAt(now)
                            .build()
            );

            throw new IllegalStateException(
                    "Signing transaction has expired"
            );
        }

        if (transaction.getStatus() != SigningTransactionStatus.AWAITING_USER_AUTHORIZATION) {
            throw new IllegalStateException("Signing transaction cannot be completed from status: " + transaction.getStatus());
        }

        logger.info(
                "Completing signing transaction",
                kv("action", "completeSignature"),
                kv("state", "initiated"),
                kv("signingTransactionId", transaction.getId()),
                kv("credentialId", transaction.getCredentialId())
        );

        final QtspTokenResponse tokenResponse = exchangeAuthorizationCode(authorizationCode, transaction.getPkceCodeVerifier());

        final QtspSignHashResponse signatureResponse = requestHashSignature(
                tokenResponse.accessToken(),
                transaction.getCredentialId(),
                transaction.getToBeSignedHashBase64(),
                transaction.getHashAlgorithmOid(),
                transaction.getSignAlgorithmOid()
        );

        final DocumentContent originalDocumentContent =
                documentContentRepository
                        .findById(
                                transaction
                                        .getDocumentContent()
                                        .getId()
                        )
                        .orElseThrow(() -> new IllegalStateException("Original document content was not found"));

        final var signedPdf = documentSigningService.sign(
                decodeCertificate(
                        transaction.getCertificateBase64()
                ),
                deserializeCertificateChain(
                        transaction.getCertificateChainJson()
                ),
                signatureResponse.firstSignature(),
                originalDocumentContent.getContent(),
                transaction.getSignatureDate(),
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
                        /*
                         * These secrets are no longer needed after completion.
                         */
//                        .oauthState(null)
//                        .pkceCodeVerifier(null)
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

        return completedTransaction.getId();
    }

    private QtspTokenResponse exchangeAuthorizationCode(
            final String authorizationCode,
            final String pkceCodeVerifier
    ) {
        /*
         * TODO: Replace the static response with:
         *
         * POST /oauth2/token
         *
         * Parameters:
         * - grant_type=authorization_code
         * - code=authorizationCode
         * - code_verifier=pkceCodeVerifier
         * - redirect_uri=<configured callback URI>
         * - client_id=<configured client ID>
         *
         * Depending on the QTSP, client authentication may also be required.
         */

        logger.info(
                "QTSP authorization code exchanged",
                kv("action", "exchangeAuthorizationCode"),
                kv("state", "succeeded")
        );

        return new QtspTokenResponse(
                "static-credential-access-token",
                "Bearer",
                120
        );
    }

    private QtspSignHashResponse requestHashSignature(
            final String accessToken,
            final String credentialId,
            final String hashBase64,
            final String hashAlgorithmOid,
            final String signAlgorithmOid
    ) {
        /*
         * TODO: Replace the static response with:
         *
         * POST /csc/v2/signatures/signHash
         *
         * Authorization: Bearer <accessToken>
         *
         * Request body:
         * {
         *   "credentialID": credentialId,
         *   "hashes": [hashBase64],
         *   "hashAlgorithmOID": hashAlgorithmOid,
         *   "signAlgo": signAlgorithmOid
         * }
         */

        final byte[] hash = decodeSha256Hash(hashBase64);
        final byte[] signature = signSha256Hash(hash);

        logger.info(
                "QTSP hash signature created",
                kv("action", "requestHashSignature"),
                kv("state", "succeeded"),
                kv("credentialId", credentialId),
                kv("hashAlgorithmOid", hashAlgorithmOid),
                kv("signAlgorithmOid", signAlgorithmOid),
                kv("signatureSize", signature.length)
        );

        return new QtspSignHashResponse(
                List.of(
                        Base64.getEncoder().encodeToString(signature)
                )
        );
    }

    private record QtspCredentialInfo(
            String credentialId,
            X509Certificate certificate,
            List<X509Certificate> certificateChain,
            String certificateBase64,
            List<String> certificateChainBase64,
            String signAlgorithmOid
    ) {}

    /*
     * Mocking
     */

    private static byte[] signSha256Hash(
            final byte[] hash
    ) {
        try {
            /*
             * The hash has already been computed by the caller.
             * NullDigest prevents hashing it a second time.
             *
             * StandardDSAEncoding returns the ASN.1 DER-encoded
             * ECDSA signature expected by normal ECDSA validators.
             */
            final var signer = new DSADigestSigner(
                    new ECDSASigner(),
                    new NullDigest(),
                    StandardDSAEncoding.INSTANCE
            );

            signer.init(
                    true,
                    PrivateKeyFactory.createKey(
                            decodePrivateKeyBytes()
                    )
            );

            signer.update(hash, 0, hash.length);

            return signer.generateSignature();
        } catch (final IOException exception) {
            throw new IllegalStateException(
                    "Could not create ECDSA signature",
                    exception
            );
        }
    }

    private static byte[] decodePrivateKeyBytes() {
        try {
            return Base64.getDecoder().decode(
                    SIGNING_PRIVATE_KEY_PKCS8_BASE64
                            .replaceAll("\\s", "")
            );
        } catch (final IllegalArgumentException exception) {
            throw new IllegalStateException(
                    "Could not decode the static RSA private key",
                    exception
            );
        }
    }

    private static byte[] decodeSha256Hash(
            final String hashBase64
    ) {
        if (hashBase64 == null || hashBase64.isBlank()) {
            throw new IllegalArgumentException(
                    "Hash to be signed is required"
            );
        }

        final byte[] hash;

        try {
            hash = Base64.getDecoder().decode(hashBase64);
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Hash is not valid Base64",
                    exception
            );
        }

        if (hash.length != 32) {
            throw new IllegalArgumentException(
                    "SHA-256 hash must contain exactly 32 bytes"
            );
        }

        return hash;
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
}
