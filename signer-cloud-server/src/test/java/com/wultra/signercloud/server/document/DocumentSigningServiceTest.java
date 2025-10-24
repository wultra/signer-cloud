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

import com.wultra.signercloud.server.configuration.PAdESConfigurationProperties;
import com.wultra.signercloud.server.signer.CertificateProcessingException;
import com.wultra.signercloud.server.signer.Signer;
import com.wultra.signercloud.server.utils.CertificateUtils;
import eu.europa.esig.dss.enumerations.*;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.SignatureFieldParameters;
import eu.europa.esig.dss.pades.SignatureImageParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.pdf.AnnotationBox;
import eu.europa.esig.dss.pdf.PdfSignatureFieldPositionChecker;
import eu.europa.esig.dss.pdf.pdfbox.PdfBoxDocumentReader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DocumentSigningService}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@ExtendWith(MockitoExtension.class)
class DocumentSigningServiceTest {

    private static final String CERTIFICATE_BASE64 = "MIIB+DCCAX6gAwIBAgIUfGkRn3KxxavJ3eeTrnhM4i+co7owCgYIKoZIzj0EAwMwFDESMBAGA1UEAwwJSXNzdWluZ0NBMB4XDTI1MDkzMDA4NTI0NFoXDTI3MDgxMTA5MTQ0NlowNjERMA8GA1UEAwwISm9obiBEb2UxFDASBgNVBAoMC0V4YW1wbGVDb3JwMQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABOiktNEsTLaUH6Mtpo1R0Kc+Lv4/qDZvD0Pwk63DujEpTPkyY8AE2pS5EByllsy7dwCVOyKgTLycL4tEjgQh/x2jgYswgYgwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAoBgNVHSUEITAfBggrBgEFBQcDAgYIKwYBBQUHAwQGCSqGSIb3LwEBBTAdBgNVHQ4EFgQUM9eY1HymMmaWtUNyhBFV0NqtPgYwDgYDVR0PAQH/BAQDAgXgMAoGCCqGSM49BAMDA2gAMGUCMQD16BkKkGNWuG8iZ3aFwMy6h907YaGr0v4jFcY+IWmND/7DK5cmx2Mta0XLMDUHqvUCMGLwsJe/o8FPpb3c6h08As/BKCqjy+AnwnmmS/RjlASoi1jGzcnegeaJOvmeK3Ii5Q==";
    private static final List<String> CERTIFICATE_CHAIN_BASE64 = List.of(
            "MIIBxzCCAU2gAwIBAgIUE0be+N9+2stvvu7y3BKDiHPWBVkwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTQ0N1oXDTI3MDgxMTA5MTQ0NlowFDESMBAGA1UEAwwJSXNzdWluZ0NBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEvq7LXohpIAISl1vcnH+8zMGFAyfEnyTOqTZAP40b9PzYmMLBbHGoDxvuJwdmF/mrXxfaQ9+Ki1/QRpkoLc6Ugsywu9agdA3Zu+54GPyxmTo8MvU/txcuRt1+7UMPxTAUo2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDaAAwZQIwMlzNpdVjPFt5/sac/ZVu/56n+vNiNFOywD8Ho8SjdDNnXeBBf3zoQ2aTwPdHtgCXAjEAkNCSl2buX5U3dsxavP2gcgjrxszNQGiQJ1AcRPL1ATHnaFrHwVGNqiFX5r9QQ7ud",
            "MIIBwzCCAUqgAwIBAgIUBiKRFuSkQ2w0B+eLnFGNCVBLTfwwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTMxMFoXDTM1MDgwOTA5MTMwOVowETEPMA0GA1UEAwwGUm9vdENBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEGTbosZgh/n+FWrbU7u05huRtjxUT8PE+fuFFHBtbcKNXYSl5Jf51gMBDn2dJKbM5oRsDLpl/nwscEvRKtibnw8AsIxXZYmyzBVA9meE5FGXswp6kAb/Sc4zQYo/O8RT5o2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBQ0YPm2cX21iY8xSP6EVfPBM2RNwTAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDZwAwZAIxAKsQhZDkBpxdGzn/gxDtqbtl5VtJFl3IJzXb36hWRf26P5Vha2vLAcFipD7koHF6bwIvYJHWRuq+SAzVYue9oId39+8AGKFXvzY+xDiSb/q7+ll/CwwQwcnoRundq8TSVYE="
    );

    private static final Instant SIGNATURE_TIMESTAMP = Instant.now();
    private static final String TO_BE_SIGNED_BASE64 = "MYG2MBgGCSqGSIb3DQEJAzELBgkqhkiG9w0BBwEwLwYJKoZIhvcNAQkEMSIEIMRmhpdGxzcg8/XQRblxNWVcfdnQBtDCHnos0HViP7riMGkGCyqGSIb3DQEJEAIvMVowWDBWMFQEIG+7rSeCMyjv8JmeFm6xWvPKLuzQQHL43Vq670xP4ZtvMDAwGKQWMBQxEjAQBgNVBAMMCUlzc3VpbmdDQQIUfGkRn3KxxavJ3eeTrnhM4i+co7o=";
    private static final String SIGNATURE_BASE64 = "MEQCIEjyKsiu8eqfbe/eJpMX16NFuHTgB0TP0unZpyryG14eAiBVqGyFDtvM+dmUbYKhIkvcmzJh2dgQ4P2a2ZNOQyhY+A==";
    private static final byte[] SIGNATURE_BYTES = Base64.getDecoder().decode(SIGNATURE_BASE64);

    private static byte[] unsignedContent;
    private static byte[] signedContent;

    private DSSDocument unsignedDssDocument;
    private DSSDocument signedDssDocument;
    private ToBeSigned toBeSigned;

    @Mock
    private PAdESConfigurationProperties pAdESConfigurationProperties;

    @Mock
    private DocumentVisualSignatureService documentVisualSignatureService;

    @Mock
    private PdfSignatureFieldPositionChecker visualSignatureChecker;

    @Mock
    private PAdESService padesService;

    @InjectMocks
    private DocumentSigningService documentSigningService;

    @BeforeAll
    static void setup() throws IOException {
        unsignedContent = new ClassPathResource("input.pdf").getContentAsByteArray();
        signedContent = new ClassPathResource("input_signed.pdf").getContentAsByteArray();
    }

    @BeforeEach
    void setupTest() {
        unsignedDssDocument = new InMemoryDocument(unsignedContent);
        signedDssDocument = new InMemoryDocument(signedContent);
        toBeSigned = new ToBeSigned(Base64.getDecoder().decode(TO_BE_SIGNED_BASE64));
    }

    @Test
    void testComputeToBeSignedWhenConvertingCertificateFailThenExceptionIsThrown() {
        // given
        final var signer = buildSigner("invalidCertificate", List.of());

        // when
        final var exception = assertThrows(
                CertificateProcessingException.class,
                () -> documentSigningService.computeToBeSigned(unsignedContent, signer, SIGNATURE_TIMESTAMP, null)
        );

        // then
        assertTrue(exception.getMessage().startsWith("Exception when processing certificate: "));
        assertNotNull(exception.getCause());
    }

    @Test
    void testComputeToBeSignedWhenConvertingCertificateChainFailThenExceptionIsThrown() {
        // given
        final var signer = buildSigner(CERTIFICATE_BASE64, List.of("invalidCertificateChain"));

        // when
        final var exception = assertThrows(
                CertificateProcessingException.class,
                () -> documentSigningService.computeToBeSigned(unsignedContent, signer, SIGNATURE_TIMESTAMP, null)
        );

        // then
        assertTrue(exception.getMessage().startsWith("Exception when processing certificate chain: "));
        assertNotNull(exception.getCause());
    }

    @Test
    void testComputeToBeSignedWhenVisualSignatureIsInvalidThenExceptionIsThrown() {
        // given
        final var signer = buildSigner(CERTIFICATE_BASE64, CERTIFICATE_CHAIN_BASE64);
        final var visualSignature = buildDocumentVisualSignature();
        final var signatureImageParameters = buildSignatureImageParameters();
        final var annotationBox = buildAnnotationBox();

        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);
        when(pAdESConfigurationProperties.getSignatureAlgorithm()).thenReturn(SignatureAlgorithm.ECDSA_SHA256);
        when(documentVisualSignatureService.createVisualSignature(eq(visualSignature), any(DSSDocument.class)))
                .thenReturn(signatureImageParameters);
        doThrow(new RuntimeException("test"))
                .when(visualSignatureChecker)
                .assertSignatureFieldPositionValid(any(PdfBoxDocumentReader.class), eq(annotationBox), eq(1));

        // when
        final var exception = assertThrows(
                DocumentVisualSignatureException.class,
                () -> documentSigningService.computeToBeSigned(unsignedContent, signer, SIGNATURE_TIMESTAMP, visualSignature)
        );

        // then
        assertEquals("test", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    @Test
    void testComputeToBeSignedWhenValidParamsWithoutVisualSignatureAreProvidedThenToBeSignedIsReturned() throws CertificateException {
        // given
        final var signer = buildSigner(CERTIFICATE_BASE64, CERTIFICATE_CHAIN_BASE64);
        final var signatureParameters = buildPAdESSignatureParameters(null, SignatureLevel.PAdES_BASELINE_B, DigestAlgorithm.SHA256);

        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);
        when(pAdESConfigurationProperties.getSignatureAlgorithm()).thenReturn(SignatureAlgorithm.ECDSA_SHA256);
        when(padesService.getDataToSign(unsignedDssDocument, signatureParameters)).thenReturn(toBeSigned);

        // when
        final var toBeSignedBase64 = documentSigningService.computeToBeSigned(unsignedContent, signer, SIGNATURE_TIMESTAMP, null);

        // then
        assertEquals(TO_BE_SIGNED_BASE64, toBeSignedBase64);
    }

    @Test
    void testComputeToBeSignedWhenValidParamsWithVisualSignatureAreProvidedThenToBeSignedIsReturned() throws CertificateException {
        // given
        final var signer = buildSigner(CERTIFICATE_BASE64, CERTIFICATE_CHAIN_BASE64);
        final var visualSignature = buildDocumentVisualSignature();
        final var signatureImageParameters = buildSignatureImageParameters();
        final var annotationBox = buildAnnotationBox();
        final var signatureParameters = buildPAdESSignatureParameters(signatureImageParameters, SignatureLevel.PAdES_BASELINE_B, DigestAlgorithm.SHA256);

        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);
        when(pAdESConfigurationProperties.getSignatureAlgorithm()).thenReturn(SignatureAlgorithm.ECDSA_SHA256);
        when(documentVisualSignatureService.createVisualSignature(eq(visualSignature), any(DSSDocument.class)))
                .thenReturn(signatureImageParameters);
        doNothing()
                .when(visualSignatureChecker)
                .assertSignatureFieldPositionValid(any(PdfBoxDocumentReader.class), eq(annotationBox), eq(1));
        when(padesService.getDataToSign(unsignedDssDocument, signatureParameters)).thenReturn(toBeSigned);

        // when
        final var toBeSignedBase64 = documentSigningService.computeToBeSigned(unsignedContent, signer, SIGNATURE_TIMESTAMP, visualSignature);

        // then
        assertEquals(TO_BE_SIGNED_BASE64, toBeSignedBase64);
    }

    @Test
    void testSignWhenSignatureIsInvalidThenExceptionIsThrown() throws CertificateException {
        // given
        final var signer = buildSigner(CERTIFICATE_BASE64, CERTIFICATE_CHAIN_BASE64);
        final var signatureParameters = buildPAdESSignatureParameters(null, SignatureLevel.PAdES_BASELINE_B, DigestAlgorithm.SHA256);
        final var signatureValue = new SignatureValue(SignatureAlgorithm.ECDSA_SHA256, SIGNATURE_BYTES);
        final var certificateToken = new CertificateToken(CertificateUtils.base64ToX509Certificate(CERTIFICATE_BASE64));

        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);
        when(padesService.getDataToSign(unsignedDssDocument, signatureParameters)).thenReturn(toBeSigned);
        when(pAdESConfigurationProperties.getSignatureAlgorithm()).thenReturn(SignatureAlgorithm.ECDSA_SHA256);
        when(padesService.isValidSignatureValue(toBeSigned, signatureValue, certificateToken)).thenReturn(false);

        // when
        final var exception = assertThrows(
                DocumentInvalidSignatureException.class,
                () -> documentSigningService.sign(signer, SIGNATURE_BASE64, unsignedContent, SIGNATURE_TIMESTAMP, null, null)
        );

        // then
        assertEquals("Invalid signature", exception.getMessage());
    }

    @Test
    void testSignWhenSignedDocumentCanNotBeReadThenExceptionIsThrown() throws CertificateException, IOException {
        // given
        final var signer = buildSigner(CERTIFICATE_BASE64, CERTIFICATE_CHAIN_BASE64);
        final var signatureParameters = buildPAdESSignatureParameters(null, SignatureLevel.PAdES_BASELINE_B, DigestAlgorithm.SHA256);
        final var signatureValue = new SignatureValue(SignatureAlgorithm.ECDSA_SHA256, SIGNATURE_BYTES);
        final var certificateToken = new CertificateToken(CertificateUtils.base64ToX509Certificate(CERTIFICATE_BASE64));
        final var signedDocument = Mockito.mock(DSSDocument.class);
        final var signedDocumentStream = Mockito.mock(InputStream.class);

        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);
        when(padesService.getDataToSign(unsignedDssDocument, signatureParameters)).thenReturn(toBeSigned);
        when(pAdESConfigurationProperties.getSignatureAlgorithm()).thenReturn(SignatureAlgorithm.ECDSA_SHA256);
        when(padesService.isValidSignatureValue(toBeSigned, signatureValue, certificateToken)).thenReturn(true);
        when(padesService.signDocument(unsignedDssDocument, signatureParameters, signatureValue)).thenReturn(signedDocument);
        when(signedDocument.openStream()).thenReturn(signedDocumentStream);
        when(signedDocumentStream.readAllBytes()).thenThrow(new IOException("test"));

        // when
        final var exception = assertThrows(
                DocumentSigningException.class,
                () -> documentSigningService.sign(signer, SIGNATURE_BASE64, unsignedContent, SIGNATURE_TIMESTAMP, null, null)
        );

        // then
        assertEquals("Exception when reading bytes of signed document: test", exception.getMessage());
        assertNotNull(exception.getCause());
    }

    @Test
    void testSignWhenValidParamsWithoutVisualSignatureAreProvidedThenSignedDocumentIsReturned() throws CertificateException {
        // given
        final var signer = buildSigner(CERTIFICATE_BASE64, CERTIFICATE_CHAIN_BASE64);
        final var signatureParameters = buildPAdESSignatureParameters(null, SignatureLevel.PAdES_BASELINE_B, DigestAlgorithm.SHA256);
        final var signatureValue = new SignatureValue(SignatureAlgorithm.ECDSA_SHA256, SIGNATURE_BYTES);
        final var certificateToken = new CertificateToken(CertificateUtils.base64ToX509Certificate(CERTIFICATE_BASE64));

        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);
        when(padesService.getDataToSign(unsignedDssDocument, signatureParameters)).thenReturn(toBeSigned);
        when(pAdESConfigurationProperties.getSignatureAlgorithm()).thenReturn(SignatureAlgorithm.ECDSA_SHA256);
        when(padesService.isValidSignatureValue(toBeSigned, signatureValue, certificateToken)).thenReturn(true);
        when(padesService.signDocument(unsignedDssDocument, signatureParameters, signatureValue)).thenReturn(signedDssDocument);

        // when
        final var signedDocument = documentSigningService.sign(signer, SIGNATURE_BASE64, unsignedContent, SIGNATURE_TIMESTAMP, null, null);

        // then
        assertArrayEquals(signedContent, signedDocument.content());
        assertEquals(DocumentSignatureLevel.PADES_B_B, signedDocument.signatureLevel());
    }

    @Test
    void testSignWhenValidParamsWithVisualSignatureAreProvidedThenSignedDocumentIsReturned() throws CertificateException {
        // given
        final var signer = buildSigner(CERTIFICATE_BASE64, CERTIFICATE_CHAIN_BASE64);
        final var visualSignature = buildDocumentVisualSignature();
        final var imageParameters = buildSignatureImageParameters();
        final var signatureParameters = buildPAdESSignatureParameters(imageParameters, SignatureLevel.PAdES_BASELINE_B, DigestAlgorithm.SHA256);
        final var signatureValue = new SignatureValue(SignatureAlgorithm.ECDSA_SHA256, SIGNATURE_BYTES);
        final var certificateToken = new CertificateToken(CertificateUtils.base64ToX509Certificate(CERTIFICATE_BASE64));

        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);
        when(padesService.getDataToSign(unsignedDssDocument, signatureParameters)).thenReturn(toBeSigned);
        when(pAdESConfigurationProperties.getSignatureAlgorithm()).thenReturn(SignatureAlgorithm.ECDSA_SHA256);
        when(documentVisualSignatureService.createVisualSignature(visualSignature, unsignedDssDocument)).thenReturn(imageParameters);
        when(padesService.isValidSignatureValue(toBeSigned, signatureValue, certificateToken)).thenReturn(true);
        when(padesService.signDocument(unsignedDssDocument, signatureParameters, signatureValue)).thenReturn(signedDssDocument);

        // when
        final var signedDocument = documentSigningService.sign(signer, SIGNATURE_BASE64, unsignedContent, SIGNATURE_TIMESTAMP, null, visualSignature);

        // then
        assertArrayEquals(signedContent, signedDocument.content());
        assertEquals(DocumentSignatureLevel.PADES_B_B, signedDocument.signatureLevel());
    }

    @Test
    void testSignWhenRequestedSignatureLevelDiffersFromDefaultOneThenRequestedLevelIsUsed() throws CertificateException {
        // given
        final var signer = buildSigner(CERTIFICATE_BASE64, CERTIFICATE_CHAIN_BASE64);
        final var signatureParameters = buildPAdESSignatureParameters(null, SignatureLevel.PAdES_BASELINE_B, DigestAlgorithm.SHA256);
        final var signatureValue = new SignatureValue(SignatureAlgorithm.ECDSA_SHA256, SIGNATURE_BYTES);
        final var certificateToken = new CertificateToken(CertificateUtils.base64ToX509Certificate(CERTIFICATE_BASE64));

        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_T);
        when(padesService.getDataToSign(unsignedDssDocument, signatureParameters)).thenReturn(toBeSigned);
        when(pAdESConfigurationProperties.getSignatureAlgorithm()).thenReturn(SignatureAlgorithm.ECDSA_SHA256);
        when(padesService.isValidSignatureValue(toBeSigned, signatureValue, certificateToken)).thenReturn(true);
        when(padesService.signDocument(unsignedDssDocument, signatureParameters, signatureValue)).thenReturn(signedDssDocument);

        // when
        final var signedDocument = documentSigningService.sign(signer, SIGNATURE_BASE64, unsignedContent, SIGNATURE_TIMESTAMP, DocumentSignatureLevel.PADES_B_B, null);

        // then
        assertArrayEquals(signedContent, signedDocument.content());
        assertEquals(DocumentSignatureLevel.PADES_B_B, signedDocument.signatureLevel());
    }

    @Test
    void testSignWhenSignatureLevelTWithoutTsaUrlIsConfiguredThenExceptionIsThrown() {
        // given
        final var signer = buildSigner(CERTIFICATE_BASE64, CERTIFICATE_CHAIN_BASE64);

        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_T);

        // when
        final var exception = assertThrows(
                TimestampAuthorityException.class,
                () -> documentSigningService.sign(signer, SIGNATURE_BASE64, unsignedContent, SIGNATURE_TIMESTAMP, null, null)
        );

        // then
        assertEquals("TSA URL not set in configuration", exception.getMessage());
    }

    @Test
    void testSignWhenSignatureLevelTWithTsaUrlIsConfigured() throws CertificateException {
        // given
        final var signer = buildSigner(CERTIFICATE_BASE64, CERTIFICATE_CHAIN_BASE64);
        final var signatureParameters = buildPAdESSignatureParameters(null, SignatureLevel.PAdES_BASELINE_T, DigestAlgorithm.SHA256);
        final var signatureValue = new SignatureValue(SignatureAlgorithm.ECDSA_SHA256, SIGNATURE_BYTES);
        final var certificateToken = new CertificateToken(CertificateUtils.base64ToX509Certificate(CERTIFICATE_BASE64));

        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_T);
        when(pAdESConfigurationProperties.getTsaUrl()).thenReturn("https://tsa.localhost");
        when(padesService.getDataToSign(unsignedDssDocument, signatureParameters)).thenReturn(toBeSigned);
        when(pAdESConfigurationProperties.getSignatureAlgorithm()).thenReturn(SignatureAlgorithm.ECDSA_SHA256);
        when(padesService.isValidSignatureValue(toBeSigned, signatureValue, certificateToken)).thenReturn(true);
        when(padesService.signDocument(unsignedDssDocument, signatureParameters, signatureValue)).thenReturn(signedDssDocument);

        // when
        final var signedDocument = documentSigningService.sign(signer, SIGNATURE_BASE64, unsignedContent, SIGNATURE_TIMESTAMP, null, null);

        // then
        assertArrayEquals(signedContent, signedDocument.content());
        assertEquals(DocumentSignatureLevel.PADES_B_T, signedDocument.signatureLevel());
    }

    @Test
    void testSignWhenSignatureAlgorithmEcdsaSha384IsConfiguredThenCorrectAlgorithmIsUsed() throws CertificateException {
        // given
        final var signer = buildSigner(CERTIFICATE_BASE64, CERTIFICATE_CHAIN_BASE64);
        final var signatureParameters = buildPAdESSignatureParameters(null, SignatureLevel.PAdES_BASELINE_B, DigestAlgorithm.SHA384);
        final var signatureValue = new SignatureValue(SignatureAlgorithm.ECDSA_SHA384, SIGNATURE_BYTES);
        final var certificateToken = new CertificateToken(CertificateUtils.base64ToX509Certificate(CERTIFICATE_BASE64));

        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);
        when(padesService.getDataToSign(unsignedDssDocument, signatureParameters)).thenReturn(toBeSigned);
        when(pAdESConfigurationProperties.getSignatureAlgorithm()).thenReturn(SignatureAlgorithm.ECDSA_SHA384);
        when(padesService.isValidSignatureValue(toBeSigned, signatureValue, certificateToken)).thenReturn(true);
        when(padesService.signDocument(unsignedDssDocument, signatureParameters, signatureValue)).thenReturn(signedDssDocument);

        // when
        final var signedDocument = documentSigningService.sign(signer, SIGNATURE_BASE64, unsignedContent, SIGNATURE_TIMESTAMP, null, null);

        // then
        assertArrayEquals(signedContent, signedDocument.content());
        assertEquals(DocumentSignatureLevel.PADES_B_B, signedDocument.signatureLevel());
    }

    private Signer buildSigner(final String certificateBase64, final List<String> certificateChain) {
        return Signer.builder()
                .certificate(certificateBase64)
                .certificateChainFromList(certificateChain)
                .build();
    }

    private DocumentVisualSignature buildDocumentVisualSignature() {
        final var fieldParams = new DocumentVisualSignature.FieldParameters(
                null,
                1,
                100f,
                200f,
                90f,
                50f,
                null
        );

        return new DocumentVisualSignature(
                null,
                300,
                DocumentVisualSignature.AlignmentHorizontal.CENTER,
                DocumentVisualSignature.AlignmentVertical.MIDDLE,
                100,
                "#4f4e4d",
                DocumentVisualSignature.ImageScaling.CENTER,
                fieldParams,
                null
        );
    }

    private AnnotationBox buildAnnotationBox() {
        final var params = new SignatureFieldParameters();
        params.setPage(1);
        params.setOriginX(100f);
        params.setOriginY(200f);
        params.setWidth(90f);
        params.setHeight(50f);

        return new AnnotationBox(params);
    }

    private SignatureImageParameters buildSignatureImageParameters() {
        final var fieldParams = new SignatureFieldParameters();
        fieldParams.setPage(1);
        fieldParams.setOriginX(100f);
        fieldParams.setOriginY(200f);
        fieldParams.setWidth(90f);
        fieldParams.setHeight(50f);

        final var params = new SignatureImageParameters();
        params.setDpi(300);
        params.setAlignmentHorizontal(VisualSignatureAlignmentHorizontal.CENTER);
        params.setAlignmentVertical(VisualSignatureAlignmentVertical.MIDDLE);
        params.setZoom(100);
        params.setBackgroundColor(Color.decode("#4f4e4d"));
        params.setImageScaling(ImageScaling.CENTER);

        params.setFieldParameters(fieldParams);

        return params;
    }

    private PAdESSignatureParameters buildPAdESSignatureParameters(
            final SignatureImageParameters imageParameters,
            final SignatureLevel signatureLevel,
            final DigestAlgorithm digestAlgorithm
    ) throws CertificateException {
        final var certificateToken = new CertificateToken(CertificateUtils.base64ToX509Certificate(CERTIFICATE_BASE64));
        final var certificateChainToken = CERTIFICATE_CHAIN_BASE64.stream()
                .map(cert -> {
                    try {
                        return new CertificateToken(CertificateUtils.base64ToX509Certificate(cert));
                    } catch (final CertificateException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        final var params = new PAdESSignatureParameters();
        params.setDigestAlgorithm(digestAlgorithm);
        params.setSigningCertificate(certificateToken);
        params.setCertificateChain(certificateChainToken);
        params.setSignatureLevel(signatureLevel);
        params.bLevel().setSigningDate(Date.from(SIGNATURE_TIMESTAMP));
        params.setSigningTimeZone(TimeZone.getTimeZone("UTC"));
        params.setImageParameters(imageParameters);

        return params;
    }
}
