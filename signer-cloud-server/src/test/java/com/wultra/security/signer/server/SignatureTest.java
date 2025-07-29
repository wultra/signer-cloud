package com.wultra.security.signer.server;

import eu.europa.esig.dss.enumerations.*;
import eu.europa.esig.dss.model.*;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.SignatureFieldParameters;
import eu.europa.esig.dss.pades.SignatureImageParameters;
import eu.europa.esig.dss.pades.SignatureImageTextParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.service.http.commons.TimestampDataLoader;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.awt.*;
import java.io.File;
import java.security.KeyStore;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
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

        try (final Pkcs12SignatureToken signingToken = new Pkcs12SignatureToken(pkcs12File, new KeyStore.PasswordProtection(password.toCharArray()))) {
            final List<DSSPrivateKeyEntry> keys = signingToken.getKeys();
            if (keys.isEmpty()) {
                fail("No private key found in the provided PKCS#12 file.");
            }
            final DSSPrivateKeyEntry privateKey = keys.get(0);

            final SignatureFieldParameters fieldParameters = new SignatureFieldParameters();
            fieldParameters.setOriginX(200);
            fieldParameters.setOriginY(400);

            final SignatureImageTextParameters textParameters = new SignatureImageTextParameters();
            textParameters.setText("Signed");
            textParameters.setTextColor(Color.BLUE);
            textParameters.setBackgroundColor(Color.YELLOW);
            textParameters.setPadding(20);
            textParameters.setTextWrapping(TextWrapping.FONT_BASED);
            textParameters.setSignerTextPosition(SignerTextPosition.LEFT);
            textParameters.setSignerTextHorizontalAlignment(SignerTextHorizontalAlignment.RIGHT);
            textParameters.setSignerTextVerticalAlignment(SignerTextVerticalAlignment.TOP);

            final SignatureImageParameters imageParameters = new SignatureImageParameters();
            imageParameters.setImage(new InMemoryDocument(getClass().getResourceAsStream("/signature-pen.png")));
            imageParameters.setFieldParameters(fieldParameters);
            imageParameters.setTextParameters(textParameters);

            final PAdESSignatureParameters parameters = new PAdESSignatureParameters();
            parameters.setSigningCertificate(privateKey.getCertificate());
            parameters.setCertificateChain(privateKey.getCertificateChain());
            parameters.setDigestAlgorithm(DigestAlgorithm.SHA384); // SHA3_384 unsupported by Adobe???
            parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_B);
            // parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_T); to make TSA working
            parameters.setImageParameters(imageParameters);

            final CertificateVerifier certificateVerifier = new CommonCertificateVerifier();
            final PAdESService padesService = new PAdESService(certificateVerifier);
            // padesService.setTspSource(createTsa());

            final ToBeSigned dataToBeSigned = padesService.getDataToSign(toSignDocument, parameters);

            final SignatureValue signatureValue = signingToken.sign(dataToBeSigned, parameters.getDigestAlgorithm(), privateKey);

            assertTrue(padesService.isValidSignatureValue(dataToBeSigned, signatureValue, privateKey.getCertificate()));

            final DSSDocument signedDocument = padesService.signDocument(toSignDocument, parameters, signatureValue);

            signedDocument.save("signed-document.pdf");
        }
    }

    @Test
    void testTsa() {
        final TSPSource tspSource = createTsa();
        final DigestAlgorithm digestAlgorithm = DigestAlgorithm.SHA384;
        final byte[] digestValue = DSSUtils.digest(digestAlgorithm, "Test data".getBytes());
        final TimestampBinary timestampToken = tspSource.getTimeStampResponse(digestAlgorithm, digestValue);
        System.out.println(DSSUtils.toHex(timestampToken.getBytes()));
    }

    private static TSPSource createTsa() {
        final String tspServer = "http://dss.nowina.lu/pki-factory/tsa/good-tsa";
        final OnlineTSPSource onlineTSPSource = new OnlineTSPSource(tspServer);
        onlineTSPSource.setDataLoader(new TimestampDataLoader());
        return onlineTSPSource;
    }
}
