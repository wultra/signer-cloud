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


import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.wultra.signercloud.server.document.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

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
public class QTSPService {
    private static final String SHA_256_OID = "2.16.840.1.101.3.4.2.1";
    private static final String RSA_SHA_256_OID = "1.2.840.113549.1.1.11";
    private static final Duration AUTHORIZATION_VALIDITY = Duration.ofMinutes(10);
    private static final long MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024;

    // TODO: put dummy into config?
    private static final URI DUMMY_AUTHORIZATION_ENDPOINT = URI.create("https://qtsp.example/oauth2/authorize");
    private static final String DUMMY_CLIENT_ID = "signature-creation-application";
    private static final String DUMMY_REDIRECT_URI = "https://sca.example/api/v1/oauth2/qtsp/callback";
    private static final String DUMMY_CERTIFICATE_BASE64 = """
            MIIDVzCCAj+gAwIBAgIUfWyU3n3GImvltv+nKStYkNjoxhUwDQYJKoZIhvcNAQELBQAwOzEaMBgGA1UEAwwRRHVtbXkgUVRTUCBTaWduZXIxEDAOBgNVBAoMB0V4YW1wbGUxCzAJBgNVBAYTAkNaMB4XDTI2MDgwMzEyNTA1NVoXDTM2MDczMTEyNTA1NVowOzEaMBgGA1UEAwwRRHVtbXkgUVRTUCBTaWduZXIxEDAOBgNVBAoMB0V4YW1wbGUxCzAJBgNVBAYTAkNaMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArrlJhG+Jmh5p0footlZIwNB9wUQQaq32pt/Qfy1JmjXgDpJsrbCcSvLjdMegF7m2BRi/M612g6TUdhA/noGPSLs+9DXa4ds9Rb4rwESOFwuzr35ngThCgwGogDcpNhWJlVqVwd1pHHuXB2JYzzS5mrroVXrrRospraRgVPAZqN9OJ9vWv6FRKvZdXttbdm/LzCdeYHFdl6jl8Ub/dOOJQP06G1AjQXDSYl8LH/GEEX9sEWa/XToEiyt7GH/mUI6GU9shCDLGa0EFUOPxJhDRSb/XUR7ZtJaTxtKPj6kwhAEzZ02ruGNAJQ7IqjqtuUP9sqRb49+qIyKvtpdkKQd0dQIDAQABo1MwUTAdBgNVHQ4EFgQUzxAZyihbCJl3cqP5BpAE8IMCx0swHwYDVR0jBBgwFoAUzxAZyihbCJl3cqP5BpAE8IMCx0swDwYDVR0TAQH/BAUwAwEB/zANBgkqhkiG9w0BAQsFAAOCAQEAXB5a8O5V+Vmr/WCPMMOrQUU+NRb9DbMJtjeNKM1k6vQdFt4FyqhSwXv65Qeny3m48o4P4yA0bJOvdWUySOeg5fucwzPk0OTotsGjtHILmpPQ2PLPIvOmHecUIKpio3V2WreESgLAmQBiMOvsStWDDcNg19/Dyod2q1q+LifEJTKESmPQQDuKFRXtdwjP2RTay2wy+5PQHCk2Y/c3syU3qCd7NtUjjxYa/bibhlAX3rkuqWtYJFp4YP3sEwlQmf3zaKJWcAFZqW0QBUWIeWW62tCBJhwbB/HHaLggI5f8KwniO9R/yzRohKcg8CKapwqCAoC3d+nTmdK0jdXzS9d0OQ==
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

        /*
         * Dummy implementation for now.
         *
         * Later, replace this method with a real QTSP call to:
         * POST /csc/v2/credentials/info
         */
        final var credentialInfo = getCredentialInfoFromQtsp(
                request.qtspSessionId(),
                request.credentialId()
        );

        final var toBeSignedHash = documentSigningService.computeToBeSigned(
                fileContent,
                credentialInfo.certificate(),
                credentialInfo.certificateChain(),
                now,
                request.visualSignature()
        );

        final var oauthState = randomBase64Url(32);
        final var pkceCodeVerifier = randomBase64Url(64);
        final var pkceCodeChallenge = createPkceCodeChallenge(pkceCodeVerifier);

        final var authorizationExpiresAt = now.plus(AUTHORIZATION_VALIDITY);

        final URI authorizationUrl = createAuthorizationUrl(
                request,
                toBeSignedHash,
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
                        .toBeSignedHashBase64(toBeSignedHash)
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
                savedTransaction.getAuthorizationExpiresAt()
        );
    }

    /**
     * Temporary replacement for a real QTSP credentials/info call.
     *
     * The arguments are intentionally accepted even though the method
     * currently returns the same static data for every request.
     */
    private DummyCredentialInfo getCredentialInfoFromQtsp(
            final String qtspSessionId,
            final String credentialId
    ) {
        logger.info(
                "Using dummy QTSP credential information",
                kv("action", "getCredentialInfo"),
                kv("qtspSessionId", qtspSessionId),
                kv("credentialId", credentialId)
        );

        final X509Certificate certificate = decodeCertificate(DUMMY_CERTIFICATE_BASE64);

        return new DummyCredentialInfo(
                credentialId,
                certificate,
                List.of(),
                DUMMY_CERTIFICATE_BASE64,
                List.of(),
                RSA_SHA_256_OID
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

    private record DummyCredentialInfo(
            String credentialId,
            X509Certificate certificate,
            List<X509Certificate> certificateChain,
            String certificateBase64,
            List<String> certificateChainBase64,
            String signAlgorithmOid
    ) {}
}
