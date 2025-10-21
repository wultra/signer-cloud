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

    public static IntTestResources prepare() throws Exception {
        if (testResources == null) {
            createResources();
        }

        return testResources;
    }

    private static void createResources() throws Exception {
        Security.addProvider(new BouncyCastleProvider());

        final var keyStore = generateKeystore();

        final var signerResources = generateSignerResources(keyStore);

        final var userPrivateKey = (PrivateKey) keyStore.getKey("user", "user".toCharArray());
        final var documentResources = generateDocumentResources(userPrivateKey, signerResources.userCertificate(), signerResources.userCertificateChain());

        final var directory = Paths.get("target", "int-test-resources");
        Files.createDirectories(directory);

        try (final var fileOutputStream = Files.newOutputStream(directory.resolve("keystore.p12"))) {
            keyStore.store(fileOutputStream, "test".toCharArray());
        }

        Files.writeString(directory.resolve("user_csr.pem"), signerResources.userCsrPem());
        Files.write(directory.resolve("signed_document.pdf"), documentResources.signedContent());

        testResources = new IntTestResources(signerResources, documentResources);
    }

    private static SignerResources generateSignerResources(final KeyStore keyStore) throws Exception {
        final var userPublicKey = keyStore.getCertificate("user").getPublicKey();
        final var userPrivateKey = (PrivateKey) keyStore.getKey("user", "user".toCharArray());
        final var userCsr = generateCSR(new KeyPair(userPublicKey, userPrivateKey));

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
                .map(c -> (X509Certificate) c )
                .toList();


        return new SignerResources(
                userCsr.getEncoded(),
                userCsrPem,
                userCriDer,
                userCsr.getSignature(),
                userCertificate,
                userCertificateChain
        );
    }

    private static DocumentResources generateDocumentResources(final PrivateKey userPrivateKey, final X509Certificate userCertificate, final List<X509Certificate> userCertificateChain) throws Exception {
        final var unsignedDocumentBytes = new ClassPathResource("input.pdf").getContentAsByteArray();
        final var documentTimestampCreated = Instant.now();
        final var signatureAlgorithm = SignatureAlgorithm.ECDSA_SHA256;

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

        final var dssDocument = new InMemoryDocument(unsignedDocumentBytes);
        final var padesService = new PAdESService(new CommonCertificateVerifier());
        final var toBeSigned = padesService.getDataToSign(dssDocument, signatureParams);
        final var documentHash = toBeSigned.getBytes();

        final var documentSignature = sign(userPrivateKey, documentHash);

        final var signedDocument = padesService.signDocument(dssDocument, signatureParams, new SignatureValue(signatureAlgorithm, documentSignature));
        final byte[] signedDocumentBytes;
        try (final var stream = signedDocument.openStream()) {
            signedDocumentBytes = stream.readAllBytes();
        }

        return new DocumentResources(
                unsignedDocumentBytes,
                documentTimestampCreated,
                documentHash,
                documentSignature,
                signedDocumentBytes
        );
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
                rootKeyPair.getPrivate(),
                "SHA384withECDSA"
        );

        final var intermediateCertificate = generateCertificate(
                INTERMEDIATE_SUBJECT,
                ROOT_SUBJECT,
                intermediateKeyPair,
                rootKeyPair.getPrivate(),
                "SHA384withECDSA"
        );

        final var userCertificate = generateCertificate(
                USER_SUBJECT,
                INTERMEDIATE_SUBJECT,
                userKeyPair,
                intermediateKeyPair.getPrivate(),
                "SHA256withECDSA"
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
            final PrivateKey issuerPrivateKey,
            final String sigAlg
    ) throws Exception {
        final var now = Instant.now();
        final var notBefore = Date.from(now);
        final var notAfter = Date.from(now.plus(365, ChronoUnit.DAYS));
        final var serialNumber = BigInteger.valueOf(System.currentTimeMillis());

        final var certificateBuilder = new JcaX509v3CertificateBuilder(
                issuer, serialNumber, notBefore, notAfter, subject, keyPair.getPublic());

        final var signer = new JcaContentSignerBuilder(sigAlg)
                .setProvider("BC")
                .build(issuerPrivateKey);

        final var certificateHolder = certificateBuilder.build(signer);
        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certificateHolder);
    }

    private static PKCS10CertificationRequest generateCSR(final KeyPair keyPair) throws Exception {
        final var signer = new JcaContentSignerBuilder("SHA256withECDSA")
                .setProvider("BC")
                .build(keyPair.getPrivate());
        return new JcaPKCS10CertificationRequestBuilder(IntTestUtils.USER_SUBJECT, keyPair.getPublic()).build(signer);
    }

    private static byte[] sign(final PrivateKey privateKey, final byte[] toBeSigned) throws Exception {
        final var signature = Signature.getInstance("SHA256withECDSA", "BC");
        signature.initSign(privateKey);
        signature.update(toBeSigned);
        return signature.sign();
    }

    public record IntTestResources(
            SignerResources signerResources,
            DocumentResources documentResources
    ) {}

    public record SignerResources(
            byte[] userCsrDer,
            String userCsrPem,
            byte[] userCriDer,
            byte[] userCsrSignature,
            X509Certificate userCertificate,
            List<X509Certificate> userCertificateChain
    ) {}

    public record DocumentResources(
            byte[] unsignedContent,
            Instant timestampCreated,
            byte[] hash,
            byte[] signature,
            byte[] signedContent
    ) {}
}
