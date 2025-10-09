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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wultra.signercloud.server.configuration.PAdESConfigurationProperties;
import com.wultra.signercloud.server.signer.*;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.SignatureImageParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import eu.europa.esig.dss.pdf.AnnotationBox;
import eu.europa.esig.dss.pdf.PdfDocumentReader;
import eu.europa.esig.dss.pdf.PdfSignatureFieldPositionChecker;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link DocumentService}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final long SIGNER_ID = 1L;
    private static final String EXTERNAL_SIGNER_ID = "756419e1-1d85-4172-815d-d8653ecd3a89";
    // private key: "Pw7qJc9ZFEMakSuAZEBYPq+0j0iuXkvqqWQzbyaNiGw="
    private static final String CERTIFICATE_BASE64 = "MIIB+DCCAX6gAwIBAgIUfGkRn3KxxavJ3eeTrnhM4i+co7owCgYIKoZIzj0EAwMwFDESMBAGA1UEAwwJSXNzdWluZ0NBMB4XDTI1MDkzMDA4NTI0NFoXDTI3MDgxMTA5MTQ0NlowNjERMA8GA1UEAwwISm9obiBEb2UxFDASBgNVBAoMC0V4YW1wbGVDb3JwMQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABOiktNEsTLaUH6Mtpo1R0Kc+Lv4/qDZvD0Pwk63DujEpTPkyY8AE2pS5EByllsy7dwCVOyKgTLycL4tEjgQh/x2jgYswgYgwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAoBgNVHSUEITAfBggrBgEFBQcDAgYIKwYBBQUHAwQGCSqGSIb3LwEBBTAdBgNVHQ4EFgQUM9eY1HymMmaWtUNyhBFV0NqtPgYwDgYDVR0PAQH/BAQDAgXgMAoGCCqGSM49BAMDA2gAMGUCMQD16BkKkGNWuG8iZ3aFwMy6h907YaGr0v4jFcY+IWmND/7DK5cmx2Mta0XLMDUHqvUCMGLwsJe/o8FPpb3c6h08As/BKCqjy+AnwnmmS/RjlASoi1jGzcnegeaJOvmeK3Ii5Q==";

    private static final String EXTERNAL_DOCUMENT_ID = "External Document Test";
    private static final String DOCUMENT_NAME = "Document Test";
    private static final String DUMMY_FILE_NAME = "dummyFileName.pdf";
    private static final String DOCUMENT_HASH = "MYG2MBgGCSqGSIb3DQEJAzELBgkqhkiG9w0BBwEwLwYJKoZIhvcNAQkEMSIEIMRmhpdGxzcg8/XQRblxNWVcfdnQBtDCHnos0HViP7riMGkGCyqGSIb3DQEJEAIvMVowWDBWMFQEIG+7rSeCMyjv8JmeFm6xWvPKLuzQQHL43Vq670xP4ZtvMDAwGKQWMBQxEjAQBgNVBAMMCUlzc3VpbmdDQQIUfGkRn3KxxavJ3eeTrnhM4i+co7o=";
    private static final String SIGNATURE = "MEQCIEjyKsiu8eqfbe/eJpMX16NFuHTgB0TP0unZpyryG14eAiBVqGyFDtvM+dmUbYKhIkvcmzJh2dgQ4P2a2ZNOQyhY+A==";
    private static final String DOCUMENT_UUID = "35282832-e15b-438d-8d5f-e4a9f006e324";

    private static final long DOCUMENT_CONTENT_ID = 2L;

    private static final String MULTIPART_FILE_FIELD_NAME = "content";

    private static final Duration WAITING_TIMEOUT = Duration.ofSeconds(60);
    private static final List<String> CERTIFICATE_CHAIN_BASE64 = List.of(
            "MIIBxzCCAU2gAwIBAgIUE0be+N9+2stvvu7y3BKDiHPWBVkwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTQ0N1oXDTI3MDgxMTA5MTQ0NlowFDESMBAGA1UEAwwJSXNzdWluZ0NBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEvq7LXohpIAISl1vcnH+8zMGFAyfEnyTOqTZAP40b9PzYmMLBbHGoDxvuJwdmF/mrXxfaQ9+Ki1/QRpkoLc6Ugsywu9agdA3Zu+54GPyxmTo8MvU/txcuRt1+7UMPxTAUo2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDaAAwZQIwMlzNpdVjPFt5/sac/ZVu/56n+vNiNFOywD8Ho8SjdDNnXeBBf3zoQ2aTwPdHtgCXAjEAkNCSl2buX5U3dsxavP2gcgjrxszNQGiQJ1AcRPL1ATHnaFrHwVGNqiFX5r9QQ7ud",
            "MIIBwzCCAUqgAwIBAgIUBiKRFuSkQ2w0B+eLnFGNCVBLTfwwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTMxMFoXDTM1MDgwOTA5MTMwOVowETEPMA0GA1UEAwwGUm9vdENBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEGTbosZgh/n+FWrbU7u05huRtjxUT8PE+fuFFHBtbcKNXYSl5Jf51gMBDn2dJKbM5oRsDLpl/nwscEvRKtibnw8AsIxXZYmyzBVA9meE5FGXswp6kAb/Sc4zQYo/O8RT5o2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBQ0YPm2cX21iY8xSP6EVfPBM2RNwTAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDZwAwZAIxAKsQhZDkBpxdGzn/gxDtqbtl5VtJFl3IJzXb36hWRf26P5Vha2vLAcFipD7koHF6bwIvYJHWRuq+SAzVYue9oId39+8AGKFXvzY+xDiSb/q7+ll/CwwQwcnoRundq8TSVYE="
    );

    @Mock
    private SignerRepository signerRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentContentRepository documentContentRepository;

    @Mock
    private DocumentConfigurationProperties documentConfigurationProperties;

    @Mock
    private PAdESService pAdESService;

    @Mock
    private PAdESConfigurationProperties pAdESConfigurationProperties;

    @Mock
    private  DocumentVisualSignatureService documentVisualSignatureService;

    @Mock
    private PdfSignatureFieldPositionChecker visualSignatureChecker;

    @InjectMocks
    private DocumentService documentService;

    @Captor
    private ArgumentCaptor<PAdESSignatureParameters> signatureParamsArgumentCaptor;

    @Captor
    private ArgumentCaptor<Document> documentArgumentCaptor;

    private byte[] uploadedDocumentContent;
    private byte[] signedDocumentContent;

    @BeforeEach
    void setUp() throws IOException {
        uploadedDocumentContent = new ClassPathResource("input.pdf").getContentAsByteArray();
        signedDocumentContent = new ClassPathResource("input_signed.pdf").getContentAsByteArray();
    }

    @Test
    void testUploadDocumentWhenUnsupportedFileTypeIsReceivedThenExceptionIsThrown() {
        // given
        final var file = new MockMultipartFile(
                MULTIPART_FILE_FIELD_NAME,
                DUMMY_FILE_NAME,
                ContentType.IMAGE_JPEG.getMimeType(),
                uploadedDocumentContent
        );

        // when
        final var exception = assertThrows(
                DocumentUploadException.class,
                () -> documentService.uploadDocument(EXTERNAL_SIGNER_ID, EXTERNAL_DOCUMENT_ID, DOCUMENT_NAME, file, null)
        );

        // then
        assertEquals("Unsupported content type: image/jpeg", exception.getMessage());
    }

    @Test
    void testUploadDocumentWhenSignerIsNotFoundThenExceptionIsThrown() {
        // given
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.empty());

        final var file = new MockMultipartFile(
                MULTIPART_FILE_FIELD_NAME,
                DUMMY_FILE_NAME,
                ContentType.APPLICATION_PDF.getMimeType(),
                uploadedDocumentContent
        );

        // when
        final var exception = assertThrows(
                SignerNotFoundException.class,
                () -> documentService.uploadDocument(EXTERNAL_SIGNER_ID, EXTERNAL_DOCUMENT_ID, DOCUMENT_NAME, file, null)
        );

        // then
        assertEquals("Signer with ID %s not found".formatted(EXTERNAL_SIGNER_ID), exception.getMessage());
    }

    @Test
    void testUploadDocumentWhenFileIsTooLargeThenExceptionIsThrown(){
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        final var file = Mockito.mock(MultipartFile.class);
        when(file.getContentType()).thenReturn(ContentType.APPLICATION_PDF.getMimeType());
        when(file.getSize()).thenReturn(1L + Integer.MAX_VALUE);

        // when
        final var exception = assertThrows(
                DocumentUploadException.class,
                () -> documentService.uploadDocument(EXTERNAL_SIGNER_ID, EXTERNAL_DOCUMENT_ID, DOCUMENT_NAME, file, null)
        );

        // then
        assertEquals("File is too large. Size: 2147483648", exception.getMessage());
    }

    @Test
    void testUploadDocumentWhenFileContentCanNotBeReadThenExceptionIsThrown() throws IOException {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        final var file = Mockito.mock(MultipartFile.class);
        when(file.getContentType()).thenReturn(ContentType.APPLICATION_PDF.getMimeType());
        when(file.getSize()).thenReturn(Long.valueOf(uploadedDocumentContent.length));
        when(file.getBytes()).thenThrow(new IOException("Test IO exception"));

        // when
        final var exception = assertThrows(
                DocumentUploadException.class,
                () -> documentService.uploadDocument(EXTERNAL_SIGNER_ID, EXTERNAL_DOCUMENT_ID, DOCUMENT_NAME, file, null)
        );

        // then
        assertEquals("Exception when reading upload file: Test IO exception", exception.getMessage());
    }

    @Test
    void testUploadDocumentWhenFileIsUploadedThenSuccessfulResponseIsReturned() {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));
        when(pAdESConfigurationProperties.getHashAlgorithm()).thenReturn(DigestAlgorithm.SHA256);
        when(pAdESService.getDataToSign(any(DSSDocument.class), any(PAdESSignatureParameters.class))).thenReturn(
                new ToBeSigned(Base64.getDecoder().decode(DOCUMENT_HASH))
        );
        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);

        final var documentContent = DocumentContent.builder()
                .id(1L)
                .content(uploadedDocumentContent)
                .build();

        when(documentContentRepository.save(any(DocumentContent.class))).thenReturn(documentContent);

        final var file = new MockMultipartFile(
                MULTIPART_FILE_FIELD_NAME,
                DUMMY_FILE_NAME,
                ContentType.APPLICATION_PDF.getMimeType(),
                uploadedDocumentContent
        );

        // when
        final var response = documentService.uploadDocument(EXTERNAL_SIGNER_ID, EXTERNAL_DOCUMENT_ID, DOCUMENT_NAME, file, null);

        // then
        assertSuccessUploadResult(response);
    }

    @Test
    void testUploadDocumentWhenFileIsUploadedThenSignatureLevelFromConfigIsUsed() {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));
        when(pAdESConfigurationProperties.getHashAlgorithm()).thenReturn(DigestAlgorithm.SHA256);
        when(pAdESService.getDataToSign(any(DSSDocument.class), any(PAdESSignatureParameters.class))).thenReturn(
                new ToBeSigned(Base64.getDecoder().decode(DOCUMENT_HASH))
        );
        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_T);

        final var documentContent = DocumentContent.builder()
                .id(1L)
                .content(uploadedDocumentContent)
                .build();

        when(documentContentRepository.save(any(DocumentContent.class))).thenReturn(documentContent);

        final var file = new MockMultipartFile(
                MULTIPART_FILE_FIELD_NAME,
                DUMMY_FILE_NAME,
                ContentType.APPLICATION_PDF.getMimeType(),
                uploadedDocumentContent
        );

        // when
        documentService.uploadDocument(EXTERNAL_SIGNER_ID, EXTERNAL_DOCUMENT_ID, DOCUMENT_NAME, file, null);

        // then
        verify(pAdESService).getDataToSign(any(DSSDocument.class), signatureParamsArgumentCaptor.capture());

        assertEquals(SignatureLevel.PAdES_BASELINE_T, signatureParamsArgumentCaptor.getValue().getSignatureLevel());
    }

    @Test
    void testUploadDocumentWhenInvalidVisualSignatureIsProvidedThenExceptionIsThrown() {
        // given
        final var visualSignature = prepareDocumentVisualSignature();

        final var signer = createSigner(SignerStatus.ACTIVE);

        final var file = new MockMultipartFile(
                MULTIPART_FILE_FIELD_NAME,
                DUMMY_FILE_NAME,
                ContentType.APPLICATION_PDF.getMimeType(),
                uploadedDocumentContent
        );

        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));
        when(pAdESConfigurationProperties.getHashAlgorithm()).thenReturn(DigestAlgorithm.SHA256);
        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);
        when(documentVisualSignatureService.createVisualSignature(eq(visualSignature), any(DSSDocument.class)))
                .thenReturn(new SignatureImageParameters());
        doThrow(new RuntimeException("Text Visual Signature Assert"))
                .when(visualSignatureChecker)
                .assertSignatureFieldPositionValid(any(PdfDocumentReader.class), any(AnnotationBox.class), eq(1));

        // when
        final var exception = assertThrows(DocumentVisualSignatureException.class, () -> documentService.uploadDocument(
                EXTERNAL_SIGNER_ID,
                EXTERNAL_DOCUMENT_ID,
                DOCUMENT_NAME,
                file,
                visualSignature
        ));

        // then
        assertEquals("Text Visual Signature Assert", exception.getMessage());
    }

    @Test
    void testUploadDocumentWhenValidVisualSignatureIsProvidedThenItIsSavedInDatabase() throws JsonProcessingException {
        // given
        final var visualSignature = prepareDocumentVisualSignature();

        final var signer = createSigner(SignerStatus.ACTIVE);

        final var file = new MockMultipartFile(
                MULTIPART_FILE_FIELD_NAME,
                DUMMY_FILE_NAME,
                ContentType.APPLICATION_PDF.getMimeType(),
                uploadedDocumentContent
        );

        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));
        when(pAdESConfigurationProperties.getHashAlgorithm()).thenReturn(DigestAlgorithm.SHA256);
        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);
        when(documentVisualSignatureService.createVisualSignature(eq(visualSignature), any(DSSDocument.class)))
                .thenReturn(new SignatureImageParameters());
        when(pAdESService.getDataToSign(any(DSSDocument.class), any(PAdESSignatureParameters.class))).thenReturn(
                new ToBeSigned(Base64.getDecoder().decode(DOCUMENT_HASH))
        );
        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);

        final var documentContent = DocumentContent.builder()
                .id(1L)
                .content(uploadedDocumentContent)
                .build();

        when(documentContentRepository.save(any(DocumentContent.class))).thenReturn(documentContent);

        // when
        documentService.uploadDocument(
                EXTERNAL_SIGNER_ID,
                EXTERNAL_DOCUMENT_ID,
                DOCUMENT_NAME,
                file,
                visualSignature
        );

        // then
        verify(documentRepository).save(documentArgumentCaptor.capture());

        final var savedDocument =  documentArgumentCaptor.getValue();
        final var expectedVisualSignatureJson = new ObjectMapper().writeValueAsBytes(prepareDocumentVisualSignature());
        assertArrayEquals(expectedVisualSignatureJson, savedDocument.getVisualSignatureJson());
    }

    @Test
    void testSignDocumentWhenDocumentIsNotFoundThenExceptionIsThrown() {
        // given
        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.empty());

        final var request = new SignDocumentRequest(SIGNATURE, null, null);

        // when
        final var exception = assertThrows(
                DocumentNotFoundException.class,
                () -> documentService.signDocument(DOCUMENT_UUID, request)
        );

        // then
        assertEquals("Document with ID %s not found".formatted(DOCUMENT_UUID), exception.getMessage());
    }

    @Test
    void testSignDocumentWhenDocumentContentIsNotFoundThenExceptionIsThrown() {
        // given
        final var document = Document.builder()
                .documentContent(AggregateReference.to(DOCUMENT_CONTENT_ID))
                .build();

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(AggregateReference.to(DOCUMENT_CONTENT_ID))).thenReturn(Optional.empty());

        final var request = new SignDocumentRequest(SIGNATURE, null, null);

        // when
        final var exception = assertThrows(
                DocumentContentNotFoundException.class,
                () -> documentService.signDocument(DOCUMENT_UUID, request)
        );

        // then
        assertEquals("Content for document ID %s not found".formatted(DOCUMENT_UUID), exception.getMessage());
    }

    @Test
    void testSignDocumentWhenSignerIsNotFoundThenExceptionIsThrown() {
        // given
        final var document = Document.builder()
                .documentContent(AggregateReference.to(DOCUMENT_CONTENT_ID))
                .signer(AggregateReference.to(SIGNER_ID))
                .build();

        final var documentContent = DocumentContent.builder().build();

        when(documentContentRepository.findById(AggregateReference.to(DOCUMENT_CONTENT_ID))).thenReturn(Optional.of(documentContent));
        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(signerRepository.findById(AggregateReference.to(SIGNER_ID))).thenReturn(Optional.empty());

        final var request = new SignDocumentRequest(SIGNATURE, null, null);

        // when
        final var exception = assertThrows(
                SignerNotFoundException.class,
                () -> documentService.signDocument(DOCUMENT_UUID, request)
        );

        // then
        assertEquals("Signer with internal ID %s not found".formatted(SIGNER_ID), exception.getMessage());
    }

    @Test
    void testSignDocumentWhenSignerIsNotActiveThenExceptionIsThrown() {
        // given
        final var document = Document.builder()
                .documentContent(AggregateReference.to(DOCUMENT_CONTENT_ID))
                .signer(AggregateReference.to(SIGNER_ID))
                .build();

        final var documentContent = DocumentContent.builder().build();

        final var signer = createSigner(SignerStatus.BLOCKED);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(AggregateReference.to(DOCUMENT_CONTENT_ID))).thenReturn(Optional.of(documentContent));
        when(signerRepository.findById(AggregateReference.to(SIGNER_ID))).thenReturn(Optional.of(signer));

        final var request = new SignDocumentRequest(SIGNATURE, null, null);

        // when
        final var exception = assertThrows(
                SignerStateException.class,
                () -> documentService.signDocument(DOCUMENT_UUID, request)
        );

        // then
        assertEquals("Signer is not active. Signer: " + EXTERNAL_SIGNER_ID, exception.getMessage());
    }

    @Test
    void testSignDocumentWhenDocumentHasNotWaitingStatusThenExceptionIsThrown() {
        // given
        final var document = Document.builder()
                .documentContent(AggregateReference.to(DOCUMENT_CONTENT_ID))
                .signer(AggregateReference.to(SIGNER_ID))
                .status(DocumentStatus.REJECTED)
                .build();

        final var documentContent = DocumentContent.builder().build();

        final var signer = createSigner(SignerStatus.ACTIVE);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(AggregateReference.to(DOCUMENT_CONTENT_ID))).thenReturn(Optional.of(documentContent));
        when(signerRepository.findById(AggregateReference.to(SIGNER_ID))).thenReturn(Optional.of(signer));

        final var request = new SignDocumentRequest(SIGNATURE, null, null);

        // when
        final var exception = assertThrows(
                DocumentStateException.class,
                () -> documentService.signDocument(DOCUMENT_UUID, request)
        );

        // then
        assertEquals("Document is not in state when it can be signed", exception.getMessage());
    }

    @Test
    void testSignDocumentWhenDocumentSignAttemptIsAfterDeadlineThenExceptionIsThrown() {
        // given
        final var document = Document.builder()
                .timestampCreated(Instant.now().minusSeconds(120))
                .documentContent(AggregateReference.to(DOCUMENT_CONTENT_ID))
                .signer(AggregateReference.to(SIGNER_ID))
                .status(DocumentStatus.WAITING)
                .build();

        final var documentContent = DocumentContent.builder().build();

        final var signer = createSigner(SignerStatus.ACTIVE);

        final var waitingDuration = new DocumentConfigurationProperties.DocumentConfiguration();
        waitingDuration.setTimeout(WAITING_TIMEOUT);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(AggregateReference.to(DOCUMENT_CONTENT_ID))).thenReturn(Optional.of(documentContent));
        when(signerRepository.findById(AggregateReference.to(SIGNER_ID))).thenReturn(Optional.of(signer));
        when(documentConfigurationProperties.getWaiting()).thenReturn(waitingDuration);

        final var request = new SignDocumentRequest(SIGNATURE, null, null);

        // when
        final var exception = assertThrows(
                DocumentStateException.class,
                () -> documentService.signDocument(DOCUMENT_UUID, request)
        );

        // then
        assertEquals("Document signing timeout exceeded", exception.getMessage());
    }

    @Test
    void testSignDocumentWhenSignatureIsNotValidThenExceptionIsThrown() {
        // given
        final var document = Document.builder()
                .timestampCreated(Instant.now())
                .documentContent(AggregateReference.to(DOCUMENT_CONTENT_ID))
                .signer(AggregateReference.to(SIGNER_ID))
                .status(DocumentStatus.WAITING)
                .hash(DOCUMENT_HASH)
                .build();

        final var documentContent = DocumentContent.builder()
                .content(uploadedDocumentContent)
                .build();

        final var signer = createSigner(SignerStatus.ACTIVE);

        final var waitingDuration = new DocumentConfigurationProperties.DocumentConfiguration();
        waitingDuration.setTimeout(WAITING_TIMEOUT);

        final var request = new SignDocumentRequest("invalidSignature", null, null);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(AggregateReference.to(DOCUMENT_CONTENT_ID))).thenReturn(Optional.of(documentContent));
        when(signerRepository.findById(AggregateReference.to(SIGNER_ID))).thenReturn(Optional.of(signer));
        when(documentConfigurationProperties.getWaiting()).thenReturn(waitingDuration);
        when(pAdESConfigurationProperties.getHashAlgorithm()).thenReturn(DigestAlgorithm.SHA256);
        when(pAdESService.getDataToSign(any(DSSDocument.class), any(PAdESSignatureParameters.class))).thenReturn(
                new ToBeSigned(Base64.getDecoder().decode(DOCUMENT_HASH))
        );
        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);

        // when
        final var exception = assertThrows(
                DocumentInvalidSignatureException.class,
                () -> documentService.signDocument(DOCUMENT_UUID, request)
        );

        // then
        assertEquals("Invalid signature", exception.getMessage());
    }

    @Test
    void testSignDocumentWhenSignatureIsValidThenSuccessResultIsReturned() {
        // given
        final var document = Document.builder()
                .timestampCreated(Instant.now())
                .documentContent(AggregateReference.to(DOCUMENT_CONTENT_ID))
                .signer(AggregateReference.to(SIGNER_ID))
                .status(DocumentStatus.WAITING)
                .hash(DOCUMENT_HASH)
                .documentId(DOCUMENT_UUID)
                .build();

        final var documentContent = DocumentContent.builder()
                .content(uploadedDocumentContent)
                .build();

        final var signer = createSigner(SignerStatus.ACTIVE);

        final var waitingDuration = new DocumentConfigurationProperties.DocumentConfiguration();
        waitingDuration.setTimeout(WAITING_TIMEOUT);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(AggregateReference.to(DOCUMENT_CONTENT_ID))).thenReturn(Optional.of(documentContent));
        when(signerRepository.findById(AggregateReference.to(SIGNER_ID))).thenReturn(Optional.of(signer));
        when(documentConfigurationProperties.getWaiting()).thenReturn(waitingDuration);
        when(pAdESConfigurationProperties.getHashAlgorithm()).thenReturn(DigestAlgorithm.SHA256);
        when(pAdESService.getDataToSign(any(DSSDocument.class), any(PAdESSignatureParameters.class)))
                .thenReturn(new ToBeSigned(Base64.getDecoder().decode(DOCUMENT_HASH)));
        when(pAdESService.isValidSignatureValue(any(ToBeSigned.class), any(SignatureValue.class), any(CertificateToken.class)))
                .thenReturn(true);
        when(pAdESService.signDocument(any(InMemoryDocument.class), any(PAdESSignatureParameters.class), any(SignatureValue.class)))
                .thenReturn(new InMemoryDocument(signedDocumentContent));
        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);

        prepareRequestContext();
        final var request = new SignDocumentRequest(SIGNATURE, null, null);

        // when
        final var response = documentService.signDocument(DOCUMENT_UUID, request);

        // then
        assertSuccessSignResult(response);
    }

    @Test
    void testSignDocumentWhenCertificateChainIsSetThenPadesServiceIsCalledWithTheChain() {
        // given
        final var document = Document.builder()
                .timestampCreated(Instant.now())
                .documentContent(AggregateReference.to(DOCUMENT_CONTENT_ID))
                .signer(AggregateReference.to(SIGNER_ID))
                .status(DocumentStatus.WAITING)
                .hash(DOCUMENT_HASH)
                .documentId(DOCUMENT_UUID)
                .build();

        final var documentContent = DocumentContent.builder()
                .content(uploadedDocumentContent)
                .build();

        final var signer = createSigner(SignerStatus.ACTIVE);

        final var waitingDuration = new DocumentConfigurationProperties.DocumentConfiguration();
        waitingDuration.setTimeout(WAITING_TIMEOUT);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(AggregateReference.to(DOCUMENT_CONTENT_ID))).thenReturn(Optional.of(documentContent));
        when(signerRepository.findById(AggregateReference.to(SIGNER_ID))).thenReturn(Optional.of(signer));
        when(documentConfigurationProperties.getWaiting()).thenReturn(waitingDuration);
        when(pAdESConfigurationProperties.getHashAlgorithm()).thenReturn(DigestAlgorithm.SHA256);
        when(pAdESService.getDataToSign(any(DSSDocument.class), any(PAdESSignatureParameters.class)))
                .thenReturn(new ToBeSigned(Base64.getDecoder().decode(DOCUMENT_HASH)));
        when(pAdESService.isValidSignatureValue(any(ToBeSigned.class), any(SignatureValue.class), any(CertificateToken.class)))
                .thenReturn(true);
        when(pAdESService.signDocument(any(InMemoryDocument.class), any(PAdESSignatureParameters.class), any(SignatureValue.class)))
                .thenReturn(new InMemoryDocument(signedDocumentContent));
        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);

        prepareRequestContext();
        final var request = new SignDocumentRequest(SIGNATURE, null, null);

        // when
        documentService.signDocument(DOCUMENT_UUID, request);

        // then
        verify(pAdESService).signDocument(any(InMemoryDocument.class), signatureParamsArgumentCaptor.capture(), any(SignatureValue.class));

        final var signatureParams = signatureParamsArgumentCaptor.getValue();
        assertCertificateChain(signatureParams.getCertificateChain());
    }

    @Test
    void testSignDocumentWhenSignatureLevelIsNotSpecifiedInRequestThenValueFromConfigIsUsed() {
        // given
        final var document = Document.builder()
                .timestampCreated(Instant.now())
                .documentContent(AggregateReference.to(DOCUMENT_CONTENT_ID))
                .signer(AggregateReference.to(SIGNER_ID))
                .status(DocumentStatus.WAITING)
                .hash(DOCUMENT_HASH)
                .documentId(DOCUMENT_UUID)
                .build();

        final var documentContent = DocumentContent.builder()
                .content(uploadedDocumentContent)
                .build();

        final var signer = createSigner(SignerStatus.ACTIVE);

        final var waitingDuration = new DocumentConfigurationProperties.DocumentConfiguration();
        waitingDuration.setTimeout(WAITING_TIMEOUT);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(AggregateReference.to(DOCUMENT_CONTENT_ID))).thenReturn(Optional.of(documentContent));
        when(signerRepository.findById(AggregateReference.to(SIGNER_ID))).thenReturn(Optional.of(signer));
        when(documentConfigurationProperties.getWaiting()).thenReturn(waitingDuration);
        when(pAdESConfigurationProperties.getHashAlgorithm()).thenReturn(DigestAlgorithm.SHA256);
        when(pAdESService.getDataToSign(any(DSSDocument.class), any(PAdESSignatureParameters.class)))
                .thenReturn(new ToBeSigned(Base64.getDecoder().decode(DOCUMENT_HASH)));
        when(pAdESService.isValidSignatureValue(any(ToBeSigned.class), any(SignatureValue.class), any(CertificateToken.class)))
                .thenReturn(true);
        when(pAdESService.signDocument(any(InMemoryDocument.class), any(PAdESSignatureParameters.class), any(SignatureValue.class)))
                .thenReturn(new InMemoryDocument(signedDocumentContent));
        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);

        prepareRequestContext();
        final var request = new SignDocumentRequest(SIGNATURE, null, null);

        // when
        documentService.signDocument(DOCUMENT_UUID, request);

        // then
        verify(pAdESService).signDocument(any(InMemoryDocument.class), signatureParamsArgumentCaptor.capture(), any(SignatureValue.class));

        assertEquals(SignatureLevel.PAdES_BASELINE_B, signatureParamsArgumentCaptor.getValue().getSignatureLevel());
    }

    @Test
    void testSignDocumentWhenSignatureLevelIsSpecifiedInRequestThenThisValueIsUsed() {
        // given
        final var document = Document.builder()
                .timestampCreated(Instant.now())
                .documentContent(AggregateReference.to(DOCUMENT_CONTENT_ID))
                .signer(AggregateReference.to(SIGNER_ID))
                .status(DocumentStatus.WAITING)
                .hash(DOCUMENT_HASH)
                .documentId(DOCUMENT_UUID)
                .build();

        final var documentContent = DocumentContent.builder()
                .content(uploadedDocumentContent)
                .build();

        final var signer = createSigner(SignerStatus.ACTIVE);

        final var waitingDuration = new DocumentConfigurationProperties.DocumentConfiguration();
        waitingDuration.setTimeout(WAITING_TIMEOUT);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(AggregateReference.to(DOCUMENT_CONTENT_ID))).thenReturn(Optional.of(documentContent));
        when(signerRepository.findById(AggregateReference.to(SIGNER_ID))).thenReturn(Optional.of(signer));
        when(documentConfigurationProperties.getWaiting()).thenReturn(waitingDuration);
        when(pAdESConfigurationProperties.getHashAlgorithm()).thenReturn(DigestAlgorithm.SHA256);
        when(pAdESService.getDataToSign(any(DSSDocument.class), any(PAdESSignatureParameters.class)))
                .thenReturn(new ToBeSigned(Base64.getDecoder().decode(DOCUMENT_HASH)));
        when(pAdESService.isValidSignatureValue(any(ToBeSigned.class), any(SignatureValue.class), any(CertificateToken.class)))
                .thenReturn(true);
        when(pAdESService.signDocument(any(InMemoryDocument.class), any(PAdESSignatureParameters.class), any(SignatureValue.class)))
                .thenReturn(new InMemoryDocument(signedDocumentContent));
        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_T);

        prepareRequestContext();
        final var request = new SignDocumentRequest(SIGNATURE, DocumentSignatureLevel.PADES_B_B, null);

        // when
        documentService.signDocument(DOCUMENT_UUID, request);

        // then
        verify(pAdESService).signDocument(any(InMemoryDocument.class), signatureParamsArgumentCaptor.capture(), any(SignatureValue.class));

        assertEquals(SignatureLevel.PAdES_BASELINE_B, signatureParamsArgumentCaptor.getValue().getSignatureLevel());
    }

    @Test
    void testSignDocumentWhenSignatureLevelTIsRequestedAndTsaUrlIsNotSetThenCorrectExceptionIsThrown() {
        // given
        final var document = Document.builder()
                .timestampCreated(Instant.now())
                .documentContent(AggregateReference.to(DOCUMENT_CONTENT_ID))
                .signer(AggregateReference.to(SIGNER_ID))
                .status(DocumentStatus.WAITING)
                .hash(DOCUMENT_HASH)
                .documentId(DOCUMENT_UUID)
                .build();

        final var documentContent = DocumentContent.builder()
                .content(uploadedDocumentContent)
                .build();

        final var signer = createSigner(SignerStatus.ACTIVE);

        final var waitingDuration = new DocumentConfigurationProperties.DocumentConfiguration();
        waitingDuration.setTimeout(WAITING_TIMEOUT);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(AggregateReference.to(DOCUMENT_CONTENT_ID))).thenReturn(Optional.of(documentContent));
        when(signerRepository.findById(AggregateReference.to(SIGNER_ID))).thenReturn(Optional.of(signer));
        when(documentConfigurationProperties.getWaiting()).thenReturn(waitingDuration);
        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);

        prepareRequestContext();
        final var request = new SignDocumentRequest(SIGNATURE, DocumentSignatureLevel.PADES_B_T, null);

        // when
        final var exception = assertThrows(TimestampAuthorityException.class, () -> documentService.signDocument(DOCUMENT_UUID, request));

        // then
        assertEquals("TSA URL not set in configuration", exception.getMessage());
    }

    @Test
    void testSignDocumentWhenSignatureLevelTIsRequestedAndTsaUrlIsSetThenDocumentIsSigned() {
        // given
        final var document = Document.builder()
                .timestampCreated(Instant.now())
                .documentContent(AggregateReference.to(DOCUMENT_CONTENT_ID))
                .signer(AggregateReference.to(SIGNER_ID))
                .status(DocumentStatus.WAITING)
                .hash(DOCUMENT_HASH)
                .documentId(DOCUMENT_UUID)
                .build();

        final var documentContent = DocumentContent.builder()
                .content(uploadedDocumentContent)
                .build();

        final var signer = createSigner(SignerStatus.ACTIVE);

        final var waitingDuration = new DocumentConfigurationProperties.DocumentConfiguration();
        waitingDuration.setTimeout(WAITING_TIMEOUT);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(AggregateReference.to(DOCUMENT_CONTENT_ID))).thenReturn(Optional.of(documentContent));
        when(signerRepository.findById(AggregateReference.to(SIGNER_ID))).thenReturn(Optional.of(signer));
        when(documentConfigurationProperties.getWaiting()).thenReturn(waitingDuration);
        when(pAdESConfigurationProperties.getSignatureLevel()).thenReturn(DocumentSignatureLevel.PADES_B_B);
        when(pAdESConfigurationProperties.getTsaUrl()).thenReturn("https://freetsa.org/tsr");
        when(pAdESConfigurationProperties.getHashAlgorithm()).thenReturn(DigestAlgorithm.SHA256);
        when(pAdESService.getDataToSign(any(DSSDocument.class), any(PAdESSignatureParameters.class)))
                .thenReturn(new ToBeSigned(Base64.getDecoder().decode(DOCUMENT_HASH)));
        when(pAdESService.isValidSignatureValue(any(ToBeSigned.class), any(SignatureValue.class), any(CertificateToken.class)))
                .thenReturn(true);
        when(pAdESService.signDocument(any(InMemoryDocument.class), any(PAdESSignatureParameters.class), any(SignatureValue.class)))
                .thenReturn(new InMemoryDocument(signedDocumentContent));

        prepareRequestContext();
        final var request = new SignDocumentRequest(SIGNATURE, DocumentSignatureLevel.PADES_B_T, null);

        // when
        documentService.signDocument(DOCUMENT_UUID, request);

        // then
        verify(pAdESService).signDocument(any(InMemoryDocument.class), any(PAdESSignatureParameters.class), any(SignatureValue.class));
    }

    @Test
    void testDownloadDocumentWhenDocumentIsNotFoundThenExceptionIsThrown() {
        // given
        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.empty());

        // when
        final var exception = assertThrows(
                DocumentNotFoundException.class,
                () -> documentService.downloadDocument(DOCUMENT_UUID)
        );

        // then
        assertEquals("Document with ID %s not found".formatted(DOCUMENT_UUID), exception.getMessage());
    }

    @Test
    void testDownloadDocumentWhenDocumentContentIsNotFoundThenExceptionIsThrown() {
        // given
        final var document = Document.builder()
                .documentContent(AggregateReference.to(DOCUMENT_CONTENT_ID))
                .build();

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(AggregateReference.to(DOCUMENT_CONTENT_ID))).thenReturn(Optional.empty());

        // when
        final var exception = assertThrows(
                DocumentContentNotFoundException.class,
                () -> documentService.downloadDocument(DOCUMENT_UUID)
        );

        // then
        assertEquals("Content for document ID %s not found".formatted(DOCUMENT_UUID), exception.getMessage());
    }

    @Test
    void testDownloadDocumentWhenDocumentIsNotSignedYetThenExceptionIsThrown() {
        // given
        final var document = Document.builder()
                .documentContent(AggregateReference.to(DOCUMENT_CONTENT_ID))
                .status(DocumentStatus.WAITING)
                .build();

        final var documentContent = DocumentContent.builder().build();

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(AggregateReference.to(DOCUMENT_CONTENT_ID))).thenReturn(Optional.of(documentContent));

        // when
        final var exception = assertThrows(
                DocumentStateException.class,
                () -> documentService.downloadDocument(DOCUMENT_UUID)
        );

        // then
        assertEquals("Document is not signed yet", exception.getMessage());
    }

    @Test
    void testDownloadDocumentWhenValidRequestIsReceivedThenResourceIsReturned() throws IOException {
        // given
        final var document = Document.builder()
                .documentContent(AggregateReference.to(DOCUMENT_CONTENT_ID))
                .status(DocumentStatus.SIGNED)
                .build();

        final var documentContent = DocumentContent.builder()
                .content(signedDocumentContent)
                .build();

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(AggregateReference.to(DOCUMENT_CONTENT_ID))).thenReturn(Optional.of(documentContent));

        // when
        final var response = documentService.downloadDocument(DOCUMENT_UUID);

        // then
        assertArrayEquals(signedDocumentContent, response.getContentAsByteArray());
    }

    @Test
    void testRejectDocumentWhenInvalidStatusIsRequestedThenExceptionIsThrown() {
        // given
        final var requestBody = new RejectDocumentRequest(DocumentStatus.SIGNED);

        // when
        final var exception = assertThrows(
                DocumentStatusTransitionException.class,
                () -> documentService.rejectDocument(DOCUMENT_UUID, requestBody)
        );

        // then
        assertEquals("Invalid status in the request body. Expected: REJECTED, actual: SIGNED", exception.getMessage());
    }

    @Test
    void testRejectDocumentWhenDocumentIsNotFoundThenExceptionIsThrown() {
        // given
        final var requestBody = new RejectDocumentRequest(DocumentStatus.REJECTED);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.empty());

        // when
        final var exception = assertThrows(
                DocumentNotFoundException.class,
                () -> documentService.rejectDocument(DOCUMENT_UUID, requestBody)
        );

        // then
        assertEquals("Document with ID %s not found".formatted(DOCUMENT_UUID), exception.getMessage());
    }

    @Test
    void testRejectDocumentWhenStatusIsChangedThenSuccessResultIsReturned() {
        // given
        final var document = Document.builder()
                .documentId(DOCUMENT_UUID)
                .documentName(DOCUMENT_NAME)
                .fileName(DUMMY_FILE_NAME)
                .fileSize(uploadedDocumentContent.length)
                .hash(DOCUMENT_HASH)
                .build();

        final var requestBody = new RejectDocumentRequest(DocumentStatus.REJECTED);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));

        // when
        final var response = documentService.rejectDocument(DOCUMENT_UUID, requestBody);

        // then
        assertRejectSuccessResult(response);
    }

    @Test
    void testDeleteDocumentWhenDocumentDoesNotExistThenNoExceptionIsThrown() {
        // given
        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.empty());

        // when / then
        assertDoesNotThrow(() -> documentService.deleteDocument(DOCUMENT_UUID));
    }

    @Test
    void testDeleteDocumentWhenDocumentExistsThenNoExceptionIsThrown() {
        // given
        final var document = Document.builder()
                .documentContent(AggregateReference.to(DOCUMENT_CONTENT_ID))
                .build();

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));

        // when / then
        assertDoesNotThrow(() -> documentService.deleteDocument(DOCUMENT_UUID));
    }

    @Test
    void testDeleteDocumentWhenDocumentExistsThenDeleteActionIsCalledOnRepositories() {
        // given
        final var document = Document.builder()
                .documentContent(AggregateReference.to(DOCUMENT_CONTENT_ID))
                .build();

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));

        // when
        documentService.deleteDocument(DOCUMENT_UUID);

        // then
        verify(documentRepository).delete(document);
        verify(documentContentRepository).deleteById(AggregateReference.to(DOCUMENT_CONTENT_ID));
    }

    private void assertSuccessUploadResult(final UploadDocumentResponse response) {
        assertDoesNotThrow(() -> UUID.fromString(response.documentId()));
        assertEquals(EXTERNAL_SIGNER_ID, response.signerId());
        assertEquals(EXTERNAL_DOCUMENT_ID, response.externalId());
        assertEquals(DOCUMENT_NAME, response.name());
        assertEquals(DUMMY_FILE_NAME, response.fileName());
        assertEquals(uploadedDocumentContent.length, response.size());
        assertEquals(DOCUMENT_HASH, response.hash());
    }

    private void assertSuccessSignResult(final SignDocumentResponse response) {
        assertEquals(DOCUMENT_UUID, response.documentId());

        final var expectedUri = String.format("https://signercloud.wultra.com:8080/documents/%s/download",
                DOCUMENT_UUID);
        assertEquals(expectedUri, response.uri());
    }

    private static Signer createSigner(final SignerStatus status) {
        return Signer.builder()
                .id(SIGNER_ID)
                .externalSignerId(EXTERNAL_SIGNER_ID)
                .certificate(CERTIFICATE_BASE64)
                .status(status)
                .certificateChainFromList(CERTIFICATE_CHAIN_BASE64)
                .build();
    }

    private void prepareRequestContext() {
        final var request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("signercloud.wultra.com");
        request.setServerPort(8080);

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void assertRejectSuccessResult(final RejectDocumentResponse response) {
        assertEquals(DOCUMENT_UUID, response.documentId());
        assertEquals(DOCUMENT_NAME, response.name());
        assertEquals(DUMMY_FILE_NAME, response.filename());
        assertEquals(uploadedDocumentContent.length, response.size());
        assertEquals(DOCUMENT_HASH, response.hash());
    }

    private void assertCertificateChain(final List<CertificateToken> certificateChain) {
        final var certificateChainBase64 = certificateChain.stream()
                .map(certificateToken -> {
                    try {
                        return certificateToken.getCertificate().getEncoded();
                    } catch (final CertificateEncodingException e) {
                        return fail("Error when encoding certificate", e);
                    }
                })
                .map(certificateBytes -> Base64.getEncoder().encodeToString(certificateBytes))
                .toList();

        assertEquals(CERTIFICATE_CHAIN_BASE64, certificateChainBase64);
    }

    private DocumentVisualSignature prepareDocumentVisualSignature() {
        return new DocumentVisualSignature(
                null,
                300,
                DocumentVisualSignature.AlignmentHorizontal.CENTER,
                DocumentVisualSignature.AlignmentVertical.MIDDLE,
                100,
                "#4f4e4d",
                DocumentVisualSignature.ImageScaling.CENTER,
                null,
                null
        );
    }
}
