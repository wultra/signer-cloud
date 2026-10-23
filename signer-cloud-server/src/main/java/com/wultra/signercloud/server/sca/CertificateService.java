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

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x500.X500NameBuilder;
import org.bouncycastle.asn1.x500.style.BCStyle;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class CertificateService {

    /**
     * Bouncy Castle provider name.
     */
    private static final String BC = BouncyCastleProvider.PROVIDER_NAME;

    /**
     * NIST P-256 / prime256v1.
     */
    private static final String EC_CURVE = "secp256r1";

    /**
     * ECDSA with SHA-256.
     *
     * OID: 1.2.840.10045.4.3.2
     */
    private static final String SIGNATURE_ALGORITHM = "SHA256withECDSA";

    /**
     * Demo CA identity.
     */
    private static final X500Name CA_NAME =
            new X500Name(
                    "CN=Demo Signing CA,O=Demo QTSP,C=CZ"
            );

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Demo CA key and certificate.
     *
     * They are regenerated whenever the application restarts.
     */
    private final KeyPair caKeyPair;
    private final X509Certificate caCertificate;

    public CertificateService() {
        registerBouncyCastle();

        try {
            this.caKeyPair = generateEcKeyPair();
            this.caCertificate = generateCaCertificate(caKeyPair);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to initialize demo certificate authority",
                    e
            );
        }
    }

    /**
     * Creates a new EC signing key and certificate for one signing request.
     *
     * A completely new key pair is generated on every call.
     *
     * @param requestId unique SCA signing request ID
     * @param pid       PID data obtained from the wallet
     * @return certificate + corresponding private key
     */
    public CertificateAndKey generateCertificate(
            final String requestId,
            final PID pid
    ) {
        validate(requestId, pid);

        try {
            /*
             * Generate a fresh signing key for this request.
             */
            KeyPair signingKeyPair = generateEcKeyPair();

            X500Name subject = buildSubject(
                    requestId,
                    pid
            );

            Instant now = Instant.now();

            /*
             * The certificate is intentionally short-lived because
             * it is created specifically for this signing request.
             */
            Date validFrom = Date.from(
                    now.minus(
                            1,
                            ChronoUnit.MINUTES
                    )
            );

            Date validUntil = Date.from(
                    now.plus(
                            365,
                            ChronoUnit.DAYS
                    )
            );

            JcaX509v3CertificateBuilder certificateBuilder =
                    new JcaX509v3CertificateBuilder(
                            CA_NAME,
                            generateSerialNumber(),
                            validFrom,
                            validUntil,
                            subject,
                            signingKeyPair.getPublic()
                    );

            JcaX509ExtensionUtils extensionUtils =
                    new JcaX509ExtensionUtils();

            /*
             * This is an end-entity certificate, not a CA.
             */
            certificateBuilder.addExtension(
                    Extension.basicConstraints,
                    true,
                    new BasicConstraints(false)
            );

            /*
             * Key is intended for document/digital signatures.
             */
            certificateBuilder.addExtension(
                    Extension.keyUsage,
                    true,
                    new KeyUsage(
                            KeyUsage.digitalSignature
                                    | KeyUsage.nonRepudiation
                    )
            );

            /*
             * Identifier of the generated signing public key.
             */
            certificateBuilder.addExtension(
                    Extension.subjectKeyIdentifier,
                    false,
                    extensionUtils.createSubjectKeyIdentifier(
                            signingKeyPair.getPublic()
                    )
            );

            /*
             * Identifier of the CA which issued this certificate.
             */
            certificateBuilder.addExtension(
                    Extension.authorityKeyIdentifier,
                    false,
                    extensionUtils.createAuthorityKeyIdentifier(
                            caCertificate
                    )
            );

            /*
             * Sign the certificate using:
             *
             * EC P-256 CA key
             * +
             * SHA-256
             * +
             * ECDSA
             */
            ContentSigner contentSigner =
                    new JcaContentSignerBuilder(
                            SIGNATURE_ALGORITHM
                    )
                            .setProvider(BC)
                            .build(
                                    caKeyPair.getPrivate()
                            );

            X509CertificateHolder holder =
                    certificateBuilder.build(
                            contentSigner
                    );

            X509Certificate certificate =
                    new JcaX509CertificateConverter()
                            .setProvider(BC)
                            .getCertificate(holder);

            /*
             * Sanity check.
             *
             * Verify that the generated certificate was actually
             * signed by our demo CA.
             */
            certificate.verify(
                    caCertificate.getPublicKey(),
                    BC
            );

            return new CertificateAndKey(
                    certificate,
                    signingKeyPair.getPrivate()
            );

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Unable to generate certificate for request: "
                            + requestId,
                    e
            );
        }
    }

    /**
     * Generate an EC P-256 key pair.
     */
    private KeyPair generateEcKeyPair()
            throws Exception {

        KeyPairGenerator generator =
                KeyPairGenerator.getInstance(
                        "EC",
                        BC
                );

        generator.initialize(
                new ECGenParameterSpec(
                        EC_CURVE
                ),
                secureRandom
        );

        return generator.generateKeyPair();
    }

    /**
     * Generate the in-memory demo CA certificate.
     *
     * The CA itself also uses:
     *
     * EC P-256
     * SHA256withECDSA
     */
    private X509Certificate generateCaCertificate(
            final KeyPair caKeyPair
    ) throws Exception {

        Instant now = Instant.now();

        JcaX509v3CertificateBuilder certificateBuilder =
                new JcaX509v3CertificateBuilder(
                        CA_NAME,
                        generateSerialNumber(),
                        Date.from(
                                now.minus(
                                        1,
                                        ChronoUnit.DAYS
                                )
                        ),
                        Date.from(
                                now.plus(
                                        3650,
                                        ChronoUnit.DAYS
                                )
                        ),
                        CA_NAME,
                        caKeyPair.getPublic()
                );

        JcaX509ExtensionUtils extensionUtils =
                new JcaX509ExtensionUtils();

        /*
         * Mark this certificate as a CA.
         */
        certificateBuilder.addExtension(
                Extension.basicConstraints,
                true,
                new BasicConstraints(true)
        );

        /*
         * CA can issue certificates and CRLs.
         */
        certificateBuilder.addExtension(
                Extension.keyUsage,
                true,
                new KeyUsage(
                        KeyUsage.keyCertSign
                                | KeyUsage.cRLSign
                )
        );

        certificateBuilder.addExtension(
                Extension.subjectKeyIdentifier,
                false,
                extensionUtils.createSubjectKeyIdentifier(
                        caKeyPair.getPublic()
                )
        );

        certificateBuilder.addExtension(
                Extension.authorityKeyIdentifier,
                false,
                extensionUtils.createAuthorityKeyIdentifier(
                        caKeyPair.getPublic()
                )
        );

        ContentSigner signer =
                new JcaContentSignerBuilder(
                        SIGNATURE_ALGORITHM
                )
                        .setProvider(BC)
                        .build(
                                caKeyPair.getPrivate()
                        );

        X509Certificate certificate =
                new JcaX509CertificateConverter()
                        .setProvider(BC)
                        .getCertificate(
                                certificateBuilder.build(
                                        signer
                                )
                        );

        /*
         * Self-signed CA.
         */
        certificate.verify(
                caKeyPair.getPublic(),
                BC
        );

        return certificate;
    }

    /**
     * Build the signer identity from PID data.
     *
     * Example:
     *
     * CN=Filip Kratochvil
     * GN=Filip
     * SN=Kratochvil
     * SERIALNUMBER=<request UUID>
     *
     * Birthdate is deliberately not included in the certificate.
     */
    private X500Name buildSubject(
            final String requestId,
            final PID pid
    ) {
        String givenName =
                pid.givenName().trim();

        String familyName =
                pid.familyName().trim();

        return new X500NameBuilder(
                BCStyle.INSTANCE
        )
                .addRDN(
                        BCStyle.GIVENNAME,
                        givenName
                )
                .addRDN(
                        BCStyle.SURNAME,
                        familyName
                )
                .addRDN(
                        BCStyle.CN,
                        givenName + " " + familyName
                )
                .addRDN(
                        BCStyle.SERIALNUMBER,
                        requestId
                )
                .build();
    }

    /**
     * X.509 certificate serial number.
     *
     * This is different from subject SERIALNUMBER, which contains
     * requestId in this demo.
     */
    private BigInteger generateSerialNumber() {
        BigInteger serialNumber;

        do {
            /*
             * RFC 5280 serial number should fit within 20 octets.
             */
            serialNumber =
                    new BigInteger(
                            159,
                            secureRandom
                    );

        } while (serialNumber.signum() <= 0);

        return serialNumber;
    }

    private void validate(
            final String requestId,
            final PID pid
    ) {
        if (requestId == null
                || requestId.isBlank()) {

            throw new IllegalArgumentException(
                    "requestId is required"
            );
        }

        if (pid == null) {
            throw new IllegalArgumentException(
                    "PID is required"
            );
        }

        if (pid.givenName() == null
                || pid.givenName().isBlank()) {

            throw new IllegalArgumentException(
                    "PID givenName is required"
            );
        }

        if (pid.familyName() == null
                || pid.familyName().isBlank()) {

            throw new IllegalArgumentException(
                    "PID familyName is required"
            );
        }
    }

    private void registerBouncyCastle() {
        if (Security.getProvider(BC) == null) {
            Security.addProvider(
                    new BouncyCastleProvider()
            );
        }
    }
}
