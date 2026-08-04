package com.wultra.signercloud.server;

import tools.jackson.databind.ObjectMapper;
import eu.europa.esig.dss.enumerations.*;
import eu.europa.esig.dss.model.*;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.SignatureFieldParameters;
import eu.europa.esig.dss.pades.SignatureImageParameters;
import eu.europa.esig.dss.pades.SignatureImageTextParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.service.http.commons.TimestampDataLoader;
import eu.europa.esig.dss.service.tsp.OnlineTSPSource;
import eu.europa.esig.dss.simplereport.SimpleReport;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.validation.CertificateVerifier;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.spi.x509.tsp.TSPSource;
import eu.europa.esig.dss.token.DSSPrivateKeyEntry;
import eu.europa.esig.dss.token.Pkcs12SignatureToken;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.Signature;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * This class contains a test for signing a PDF document using a PKCS#12 keystore.
 * It demonstrates how to create a signature token, retrieve the private key,
 * and sign a document with PAdES parameters using the DSS (Digital Signature Service) library over Apache PDFBox.
 */
@Slf4j
class SignatureTest {

    private static final String KEYSTORE_PASSWORD = "password";
    private static final String KEYSTORE_ECDSA_P_12 = "keystore-ecdsa.p12";

    @Test
    void testDssSignature() throws Exception {
        testSignature(SignatureType.DSS);
    }

    @Test
    @Disabled("Disabled in GitHub Actions")
    void testExternalSignature() throws Exception {
        testSignature(SignatureType.EXTERNAL);
    }

    private void testSignature(final SignatureType signatureType) throws Exception {
        final File pdfFile = new ClassPathResource("input.pdf").getFile();
        final DSSDocument toSignDocument = new FileDocument(pdfFile);
        final Date signingDate = new Date();

        // keytool -genkeypair -alias myAlias -keyalg RSA -keysize 2048 -keystore keystore-rsa.p12 -storetype PKCS12 -validity 365
        // keytool -genkeypair -alias myAlias -keyalg EC -groupname secp384r1 -keystore keystore-ecdsa.p12 -storetype PKCS12 -validity 365
        final File pkcs12File = new ClassPathResource(KEYSTORE_ECDSA_P_12).getFile();

        try (final Pkcs12SignatureToken signingToken = new Pkcs12SignatureToken(pkcs12File, new KeyStore.PasswordProtection(KEYSTORE_PASSWORD.toCharArray()))) {
            final List<DSSPrivateKeyEntry> keys = signingToken.getKeys();
            if (keys.isEmpty()) {
                fail("No private key found in the provided PKCS#12 file.");
            }
            final DSSPrivateKeyEntry privateKey = keys.get(0);

            final PAdESSignatureParameters parametersForHash = createPAdESSignatureParameters(privateKey, signingDate);

            final CertificateVerifier certificateVerifier = new CommonCertificateVerifier();
            final PAdESService padesService = new PAdESService(certificateVerifier);
            // padesService.setTspSource(createTsa()); to make TSA working

            final ToBeSigned dataToBeSignedForHash = padesService.getDataToSign(toSignDocument, parametersForHash);

            final SignatureValue signatureValue = switch (signatureType) {
                case DSS -> signingToken.sign(dataToBeSignedForHash, parametersForHash.getSignatureAlgorithm(), privateKey);
                case EXTERNAL -> new SignatureValue(parametersForHash.getSignatureAlgorithm(), signExternally(dataToBeSignedForHash.getBytes()));
            };

            // In our case, we generate the hash and assemble the signed document in two isolated steps.
            // That’s why we need to create new instances of the PAdESSignatureParameters and the ToBeSigned object.
            // We need to ensure that the context is exactly the same in both steps.
            // Even the smallest difference will cause the signature to be invalid in the signed document.
            final PAdESSignatureParameters parametersForAssembling = createPAdESSignatureParameters(privateKey, signingDate);
            final ToBeSigned dataToBeSignedForAssembling = padesService.getDataToSign(toSignDocument, parametersForAssembling);

            assertTrue(padesService.isValidSignatureValue(dataToBeSignedForAssembling, signatureValue, privateKey.getCertificate()));

            final DSSDocument signedDocument = padesService.signDocument(toSignDocument, parametersForAssembling, signatureValue);

            final String targetFilePath = "target/signed-document-" + UUID.randomUUID() + ".pdf";
            signedDocument.save(targetFilePath);
            logger.info("Signed document saved to: {}, signatureType={}", targetFilePath, signatureType);

            validateDocument(signedDocument, certificateVerifier);
        }
    }

    /**
     * Simulates signing externally via SDK, when DSS does not know the private key.
     */
    private byte[] signExternally(final byte[] dataToBeSigned) throws Exception {
        final KeyStore keyStore = KeyStore.getInstance("PKCS12");
        try (final FileInputStream inputStream = new FileInputStream(new ClassPathResource(KEYSTORE_ECDSA_P_12).getFile())) {
            keyStore.load(inputStream, KEYSTORE_PASSWORD.toCharArray());
        }

        final PrivateKey privateKey = (PrivateKey) keyStore.getKey("myAlias", KEYSTORE_PASSWORD.toCharArray());
        final Signature signature = Signature.getInstance("SHA384withECDSA");
        signature.initSign(privateKey);
        signature.update(dataToBeSigned);
        return signature.sign();
    }

    private static void validateDocument(final DSSDocument signedDocument, final CertificateVerifier certificateVerifier) throws Exception {
        final SignedDocumentValidator documentValidator = SignedDocumentValidator.fromDocument(signedDocument);
        documentValidator.setCertificateVerifier(certificateVerifier);
        final Reports reports = documentValidator.validateDocument();
        final SimpleReport simpleReport = reports.getSimpleReport();
        logger.info("Validation report: {}", new ObjectMapper().writeValueAsString(simpleReport.getJaxbModel()));
    }

    @Test
    void testTsa() {
        final TSPSource tspSource = createTsa();
        final DigestAlgorithm digestAlgorithm = DigestAlgorithm.SHA384;
        final byte[] digestValue = DSSUtils.digest(digestAlgorithm, "Test data".getBytes());
        final TimestampBinary timestampToken = tspSource.getTimeStampResponse(digestAlgorithm, digestValue);
        System.out.println(DSSUtils.toHex(timestampToken.getBytes()));
    }

    private static PAdESSignatureParameters createPAdESSignatureParameters(final DSSPrivateKeyEntry privateKey, final Date signingDate) {
        final PAdESSignatureParameters parameters = new PAdESSignatureParameters();
        parameters.setSigningCertificate(privateKey.getCertificate());
        parameters.setCertificateChain(privateKey.getCertificateChain());
        // according to ETSI TS 119 312 V1.5.1 (2024-12), shall support SHA-256, SHA-384, SHA-512; should support SHA3
        // SHA3_384 is not supported by Adobe Acrobat Reader
        parameters.setDigestAlgorithm(DigestAlgorithm.SHA384);
        parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_B);
        // parameters.setSignatureLevel(SignatureLevel.PAdES_BASELINE_T); to make TSA working
        parameters.setImageParameters(createSignatureImageParameters());
        parameters.bLevel().setSigningDate(signingDate);
        return parameters;
    }

    private static SignatureImageParameters createSignatureImageParameters() {
        final SignatureImageParameters imageParameters = new SignatureImageParameters();
        imageParameters.setImage(new InMemoryDocument(SignatureTest.class.getResourceAsStream("/signature-pen.png")));
        imageParameters.setFieldParameters(createSignatureFieldParameters());
        imageParameters.setTextParameters(createSignatureImageTextParameters());
        return imageParameters;
    }

    private static SignatureFieldParameters createSignatureFieldParameters() {
        final SignatureFieldParameters fieldParameters = new SignatureFieldParameters();
        fieldParameters.setOriginX(200);
        fieldParameters.setOriginY(400);
        return fieldParameters;
    }

    private static SignatureImageTextParameters createSignatureImageTextParameters() {
        final SignatureImageTextParameters textParameters = new SignatureImageTextParameters();
        textParameters.setText("Signed");
        textParameters.setTextColor(Color.BLUE);
        textParameters.setBackgroundColor(Color.YELLOW);
        textParameters.setPadding(20);
        textParameters.setTextWrapping(TextWrapping.FONT_BASED);
        textParameters.setSignerTextPosition(SignerTextPosition.LEFT);
        textParameters.setSignerTextHorizontalAlignment(SignerTextHorizontalAlignment.RIGHT);
        textParameters.setSignerTextVerticalAlignment(SignerTextVerticalAlignment.TOP);
        return textParameters;
    }

    private static TSPSource createTsa() {
        final String tspServer = "http://dss.nowina.lu/pki-factory/tsa/good-tsa";
        final OnlineTSPSource onlineTSPSource = new OnlineTSPSource(tspServer);
        onlineTSPSource.setDataLoader(new TimestampDataLoader());
        return onlineTSPSource;
    }

    enum SignatureType {
        DSS,
        EXTERNAL
    }
}
