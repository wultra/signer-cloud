package com.wultra.security.signer.server;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.FileDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.File;
import java.security.KeyStore;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Temporary Proof of Concept.
 * <br>
 * This class contains a test for signing a PDF document using a PKCS#12 keystore.
 * It demonstrates how to create a signature token, retrieve the private key,
 * and sign a document with PAdES parameters.
 */
class SignatureTest {

    @Test
    void testSignature() throws Exception {
        final File pdfFile = new ClassPathResource("input.pdf").getFile();
        final DSSDocument toSignDocument = new FileDocument(pdfFile);

        final File pkcs12File = new ClassPathResource("keystore.p12").getFile();
        final String password = "password";

        // Create the token for signing
        try (final Pkcs12SignatureToken signingToken = new Pkcs12SignatureToken(pkcs12File, new KeyStore.PasswordProtection(password.toCharArray()))) {
            final List<DSSPrivateKeyEntry> keys = signingToken.getKeys();
            if (keys.isEmpty()) {
                fail("No private key found in the provided PKCS#12 file.");
            }
            final DSSPrivateKeyEntry privateKey = keys.get(0);

            final PAdESSignatureParameters parameters = new PAdESSignatureParameters();
            parameters.setSigningCertificate(privateKey.getCertificate());
            parameters.setCertificateChain(privateKey.getCertificateChain());
            parameters.setDigestAlgorithm(DigestAlgorithm.SHA3_384);
            parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_B);

            final CertificateVerifier certificateVerifier = new CommonCertificateVerifier();
            final PAdESService padesService = new PAdESService(certificateVerifier);

            final ToBeSigned dataToBeSigned = padesService.getDataToSign(toSignDocument, parameters);

            final SignatureValue signatureValue = signingToken.sign(dataToBeSigned, parameters.getDigestAlgorithm(), privateKey);
            final DSSDocument signedDocument = padesService.signDocument(toSignDocument, parameters, signatureValue);

            signedDocument.save("signed-document.pdf");
        }
    }
}
