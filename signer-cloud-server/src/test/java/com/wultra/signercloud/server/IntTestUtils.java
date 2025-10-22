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
package com.wultra.signercloud.server;

import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import lombok.Builder;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;
import org.springframework.core.io.ClassPathResource;

import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.*;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Class for generating integration test resources.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
public final class IntTestUtils {

    private static final X500Name ROOT_SUBJECT = new X500Name("CN=Root CA, O=Wultra, C=CZ");
    private static final X500Name INTERMEDIATE_SUBJECT = new X500Name("CN=Intermediate CA, O=Wultra, C=CZ");
    private static final X500Name USER_SUBJECT = new X500Name("CN=User CA, O=Wultra, C=CZ");

    private static IntTestResources testResources;

    /**
     * Generates and stores resources (keystore, CSR, signed documents) needed for integration tests.
     *
     * @return generated resources
     * @throws Exception when generation or storing of resources fails
     */
    public static IntTestResources prepare() throws Exception {
        if (testResources == null) {
            createResources();
        }

        return testResources;
    }

    private static void createResources() throws Exception {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        final var keyStore = generateKeystore();

        final var signerResources = generateSignerResources(keyStore);

        final var userPrivateKey = (PrivateKey) keyStore.getKey("user", "user".toCharArray());
        final var documentResources = generateDocumentResources(userPrivateKey, signerResources.userCertificate(), signerResources.userCertificateChain());

        final var directory = Paths.get("target", "test-resources");
        Files.createDirectories(directory);

        try (final var fileOutputStream = Files.newOutputStream(directory.resolve("keystore.p12"))) {
            keyStore.store(fileOutputStream, "test".toCharArray());
        }

        Files.writeString(directory.resolve("user_csr.pem"), signerResources.userCsrPem());
        Files.write(directory.resolve("signed-document-sha256.pem"), documentResources.signedContentSha256());
        Files.write(directory.resolve("signed-document-sha384.pem"), documentResources.signedContentSha384());

        testResources = new IntTestResources(signerResources, documentResources);
    }

    private static SignerResources generateSignerResources(final KeyStore keyStore) throws Exception {
        final var userPublicKey = keyStore.getCertificate("user").getPublicKey();
        final var userPrivateKey = (PrivateKey) keyStore.getKey("user", "user".toCharArray());
        final var userCsr = generateCSR(new KeyPair(userPublicKey, userPrivateKey), "SHA256withECDSA");

        final var stringWriter = new StringWriter();
        try (final var pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(userCsr);
        }
        final var userCsrPem = stringWriter.toString();

        final var userCriDer = userCsr.toASN1Structure()
                .getCertificationRequestInfo()
                .getEncoded();

        final var userCertificate = (X509Certificate) keyStore.getCertificate("user");
        final var userCertificateChain = Arrays.stream(keyStore.getCertificateChain("user"))
                .filter(c -> !c.equals(userCertificate))
                .map(c -> (X509Certificate) c)
                .toList();

        return SignerResources.builder()
                .userCsrDer(userCsr.getEncoded())
                .userCsrPem(userCsrPem)
                .userCriDer(userCriDer)
                .userCsrSignature(userCsr.getSignature())
                .userCertificate(userCertificate)
                .userCertificateChain(userCertificateChain)
                .build();
    }

    private static DocumentResources generateDocumentResources(final PrivateKey userPrivateKey, final X509Certificate userCertificate, final List<X509Certificate> userCertificateChain) throws Exception {
        final var unsignedDocumentBytes = new ClassPathResource("input.pdf").getContentAsByteArray();
        final var documentTimestampCreated = Instant.now();
        final var dssDocument = new InMemoryDocument(unsignedDocumentBytes);
        final var padesService = new PAdESService(new CommonCertificateVerifier());

        // SHA256
        final var signatureParamsSha256 = buildPadESSignatureParameters(userCertificate, userCertificateChain, SignatureAlgorithm.ECDSA_SHA256, documentTimestampCreated);
        final var toBeSignedSha256 = padesService.getDataToSign(dssDocument, signatureParamsSha256);
        final var documentHashSha256 = toBeSignedSha256.getBytes();
        final var documentSignatureSha256 = sign(userPrivateKey, documentHashSha256, "SHA256withECDSA");

        final var signedDocumentSha256 = padesService.signDocument(dssDocument, signatureParamsSha256, new SignatureValue(SignatureAlgorithm.ECDSA_SHA256, documentSignatureSha256));
        final byte[] signedDocumentBytesSha256;
        try (final var stream = signedDocumentSha256.openStream()) {
            signedDocumentBytesSha256 = stream.readAllBytes();
        }

        // SHA384
        final var signatureParamsSha384 = buildPadESSignatureParameters(userCertificate, userCertificateChain, SignatureAlgorithm.ECDSA_SHA384, documentTimestampCreated);
        final var toBeSignedSha384 = padesService.getDataToSign(dssDocument, signatureParamsSha384);
        final var documentHashSha384 = toBeSignedSha384.getBytes();
        final var documentSignatureSha384 = sign(userPrivateKey, documentHashSha384, "SHA384withECDSA");

        final var signedDocumentSha384 = padesService.signDocument(dssDocument, signatureParamsSha384, new SignatureValue(SignatureAlgorithm.ECDSA_SHA384, documentSignatureSha384));
        final byte[] signedDocumentBytesSha384;
        try (final var stream = signedDocumentSha384.openStream()) {
            signedDocumentBytesSha384 = stream.readAllBytes();
        }

        return DocumentResources.builder()
                .unsignedContent(unsignedDocumentBytes)
                .timestampCreated(documentTimestampCreated)
                .hashSha256(documentHashSha256)
                .signatureSha256(documentSignatureSha256)
                .signedContentSha256(signedDocumentBytesSha256)
                .hashSha384(documentHashSha384)
                .signatureSha384(documentSignatureSha384)
                .signedContentSha384(signedDocumentBytesSha384)
                .build();
    }

    private static KeyStore generateKeystore() throws Exception {
        final var keyPairGenerator = KeyPairGenerator.getInstance("EC", "BC");
        keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());

        final var rootKeyPair = keyPairGenerator.generateKeyPair();
        final var intermediateKeyPair = keyPairGenerator.generateKeyPair();
        final var userKeyPair = keyPairGenerator.generateKeyPair();

        final var rootCertificate = generateCertificate(
                ROOT_SUBJECT,
                ROOT_SUBJECT,
                rootKeyPair,
                rootKeyPair.getPrivate()
        );

        final var intermediateCertificate = generateCertificate(
                INTERMEDIATE_SUBJECT,
                ROOT_SUBJECT,
                intermediateKeyPair,
                rootKeyPair.getPrivate()
        );

        final var userCertificate = generateCertificate(
                USER_SUBJECT,
                INTERMEDIATE_SUBJECT,
                userKeyPair,
                intermediateKeyPair.getPrivate()
        );

        final var keyStore = KeyStore.getInstance("PKCS12");
        keyStore.load(null, null);

        keyStore.setKeyEntry(
                "root",
                rootKeyPair.getPrivate(),
                "root".toCharArray(),
                new X509Certificate[]{rootCertificate}
        );

        keyStore.setKeyEntry(
                "intermediate",
                intermediateKeyPair.getPrivate(),
                "intermediate".toCharArray(),
                new X509Certificate[]{intermediateCertificate, rootCertificate}
        );

        keyStore.setKeyEntry(
                "user",
                userKeyPair.getPrivate(),
                "user".toCharArray(),
                new X509Certificate[]{userCertificate, intermediateCertificate, rootCertificate}
        );

        return keyStore;
    }

    private static X509Certificate generateCertificate(
            final X500Name subject,
            final X500Name issuer,
            final KeyPair keyPair,
            final PrivateKey issuerPrivateKey
    ) throws Exception {
        final var now = Instant.now();
        final var notBefore = Date.from(now);
        final var notAfter = Date.from(now.plus(365, ChronoUnit.DAYS));
        final var serialNumber = BigInteger.valueOf(System.currentTimeMillis());

        final var certificateBuilder = new JcaX509v3CertificateBuilder(
                issuer, serialNumber, notBefore, notAfter, subject, keyPair.getPublic());

        final var signer = new JcaContentSignerBuilder("SHA384withECDSA")
                .setProvider("BC")
                .build(issuerPrivateKey);

        final var certificateHolder = certificateBuilder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certificateHolder);
    }

    private static PKCS10CertificationRequest generateCSR(final KeyPair keyPair, String signatureAlgorithm) throws Exception {
        final var signer = new JcaContentSignerBuilder(signatureAlgorithm)
                .setProvider("BC")
                .build(keyPair.getPrivate());
        return new JcaPKCS10CertificationRequestBuilder(IntTestUtils.USER_SUBJECT, keyPair.getPublic()).build(signer);
    }

    private static PAdESSignatureParameters buildPadESSignatureParameters(
            final X509Certificate userCertificate,
            final List<X509Certificate> userCertificateChain,
            final SignatureAlgorithm signatureAlgorithm,
            final Instant documentTimestampCreated
    ) {
        final var certificateChain = userCertificateChain.stream()
                .map(CertificateToken::new)
                .toList();

        final var signatureParams = new PAdESSignatureParameters();
        signatureParams.setDigestAlgorithm(signatureAlgorithm.getDigestAlgorithm());
        signatureParams.setSigningCertificate(new CertificateToken(userCertificate));
        signatureParams.setCertificateChain(certificateChain);
        signatureParams.setSignatureLevel(SignatureLevel.PAdES_BASELINE_B);
        signatureParams.setSigningTimeZone(TimeZone.getTimeZone("UTC"));
        signatureParams.bLevel().setSigningDate(Date.from(documentTimestampCreated));
        return signatureParams;
    }

    private static byte[] sign(final PrivateKey privateKey, final byte[] toBeSigned, String signatureAlgorithm) throws Exception {
        final var signature = Signature.getInstance(signatureAlgorithm, "BC");
        signature.initSign(privateKey);
        signature.update(toBeSigned);
        return signature.sign();
    }

    public record IntTestResources(
            SignerResources signerResources,
            DocumentResources documentResources
    ) {}

    @Builder
    public record SignerResources(
            byte[] userCsrDer,
            String userCsrPem,
            byte[] userCriDer,
            byte[] userCsrSignature,
            X509Certificate userCertificate,
            List<X509Certificate> userCertificateChain
    ) {}

    @Builder
    public record DocumentResources(
            byte[] unsignedContent,
            Instant timestampCreated,
            byte[] hashSha256,
            byte[] signatureSha256,
            byte[] signedContentSha256,
            byte[] hashSha384,
            byte[] signatureSha384,
            byte[] signedContentSha384
    ) {}
}
