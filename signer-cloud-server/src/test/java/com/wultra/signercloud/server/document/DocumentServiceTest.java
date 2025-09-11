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

import com.wultra.signercloud.server.restapi.Try;
import com.wultra.signercloud.server.signer.Signer;
import com.wultra.signercloud.server.signer.SignerNotFoundException;
import com.wultra.signercloud.server.signer.SignerRepository;
import com.wultra.signercloud.server.signer.SignerStatus;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.PAdESSignatureParameters;
import eu.europa.esig.dss.pades.signature.PAdESService;
import org.apache.hc.core5.http.ContentType;
import org.bouncycastle.util.Arrays;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocumentService}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final long SIGNER_ID = 1L;
    private static final String DUMMY_EXTERNAL_SIGNER_ID = "dummyExternalSignerId";
    private static final String CERTIFICATE = "MIICFDCCAZugAwIBAgIUC0O75BZKicH8bDlRUBgC8h7bTdgwCgYIKoZIzj0EAwMwFDESMBAGA1UEAwwJSXNzdWluZ0NBMB4XDTI1MDgyNzA3NTUyOVoXDTI3MDgxMTA5MTQ0NlowNjERMA8GA1UEAwwISm9obiBEb2UxFDASBgNVBAoMC0V4YW1wbGVDb3JwMQswCQYDVQQGEwJVUzB2MBAGByqGSM49AgEGBSuBBAAiA2IABENmfWbIOOXtmGfPQdN7oY8+y0pF7rliVvg709N7X3Q/uW5g7rTO7JWc4BPX8+rGjbkaAU7tO6nt0JCaBtYlwzmGLkwywafNd4iTsFaZRbFLNdZue/c0poVN9RmwLTOH/6OBizCBiDAMBgNVHRMBAf8EAjAAMB8GA1UdIwQYMBaAFJ0dk1DJP8vLqD/Dx15EMOEpmqkOMCgGA1UdJQQhMB8GCCsGAQUFBwMCBggrBgEFBQcDBAYJKoZIhvcvAQEFMB0GA1UdDgQWBBT9ooCMF8fza6pXM3q4FQrfJ0nkhjAOBgNVHQ8BAf8EBAMCBeAwCgYIKoZIzj0EAwMDZwAwZAIwaeS/siF1g5vbaNXrnQM9xJOQmUG92HyNOCTKh/x1PA9b/VwtpodSjkIOiOxJQ56aAjBQit9XczUVNp5qGdrLO3Ac730VokRvphNBtupJbdnkpywejktZi00LM8MsuZA7Piw=";

    private static final String DUMMY_EXTERNAL_DOCUMENT_ID = "dummyExternalDocumentId";
    private static final String DUMMY_DOCUMENT_NAME = "dummyDocumentName";
    private static final String DUMMY_FILE_NAME = "dummyFileName.pdf";
    private static final String DOCUMENT_HASH = "x/PQFGarKCBiFs2lzQkH4QDtxBqR+6e6YQSomQEWv+U=";
    private static final String SIGNATURE = "MGUCMAsFMLMgPLrn5e4BFS1UeFgMy/6hrSvsamClvy6cfuC1oUTc8Zecz9i0pkgor4o7vQIxAIcJ+/d+Vcbh1DCpp15xsAtA3lOXY/KLVp0CVypNtBS/+6W0XxSP3s08y/Y+uCJvSw==";
    private static final String DOCUMENT_UUID = UUID.randomUUID().toString();

    private static final long DOCUMENT_CONTENT_ID = 2L;
    private static final byte[] UPLOADED_DOCUMENT_CONTENT = Base64.getDecoder().decode("JVBERi0xLjEKMSAwIG9iago8PC9UeXBlIC9DYXRhbG9nIC9QYWdlcyAyIDAgUiA+PgplbmRvYmoKMiAwIG9iago8PC9UeXBlIC9QYWdlcyAvS2lkcyBbMyAwIFJdIC9Db3VudCAxID4+CmVuZG9iagozIDAgb2JqCjw8L1R5cGUgL1BhZ2UgL1BhcmVudCAyIDAgUiAvTWVkaWFCb3ggWzAgMCAxMCAxMF0gPj4KZW5kb2JqCnRyYWlsZXIKPDwvUm9vdCAxIDAgUiA+PnN0YXJ0eHJlZjoxMjMKJSVFT0YK");
    private static final byte[] SIGNED_DOCUMENT_CONTENT = Arrays.concatenate(UPLOADED_DOCUMENT_CONTENT, "SIGNED".getBytes());

    private static final String MULTIPART_FILE_FIELD_NAME = "content";

    private static final Duration WAITING_TIMEOUT = Duration.ofSeconds(60);

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

    @InjectMocks
    private DocumentService documentService;

    @Test
    void testUploadDocumentWhenUnsupportedFileTypeIsReceivedThenFailResultWithCorrectMessageIsReturned() throws NoSuchAlgorithmException {
        // given
        final var file = new MockMultipartFile(
                MULTIPART_FILE_FIELD_NAME,
                DUMMY_FILE_NAME,
                ContentType.IMAGE_JPEG.getMimeType(),
                UPLOADED_DOCUMENT_CONTENT
        );

        // when
        final var result = documentService.uploadDocument(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_EXTERNAL_DOCUMENT_ID, DUMMY_DOCUMENT_NAME, file);

        // then
        assertFailResult(result, DocumentUploadException.class, "Unsupported content type: image/jpeg");
    }

    @Test
    void testUploadDocumentWhenSignerIsNotFoundThenFailResultWithCorrectMessageIsReturned() throws NoSuchAlgorithmException {
        // given
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.empty());

        final var file = new MockMultipartFile(
                MULTIPART_FILE_FIELD_NAME,
                DUMMY_FILE_NAME,
                ContentType.APPLICATION_PDF.getMimeType(),
                UPLOADED_DOCUMENT_CONTENT
        );

        // when
        final var result = documentService.uploadDocument(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_EXTERNAL_DOCUMENT_ID, DUMMY_DOCUMENT_NAME, file);

        // then
        assertFailResult(result, SignerNotFoundException.class, "Signer not found for external signer ID: dummyExternalSignerId");
    }

    @Test
    void testUploadDocumentWhenFileIsTooLargeThenFailResultWithCorrectMessageIsReturned() throws NoSuchAlgorithmException {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        final var file = Mockito.mock(MultipartFile.class);
        when(file.getContentType()).thenReturn(ContentType.APPLICATION_PDF.getMimeType());
        when(file.getSize()).thenReturn(1L + Integer.MAX_VALUE);

        // when
        final var result = documentService.uploadDocument(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_EXTERNAL_DOCUMENT_ID, DUMMY_DOCUMENT_NAME, file);

        // then
        assertFailResult(result, DocumentUploadException.class, "File is too large. Size: 2147483648");
    }

    @Test
    void testUploadDocumentWhenFileContentCanNotBeReadThenFailResultWithCorrectMessageIsReturned() throws NoSuchAlgorithmException, IOException {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        final var file = Mockito.mock(MultipartFile.class);
        when(file.getContentType()).thenReturn(ContentType.APPLICATION_PDF.getMimeType());
        when(file.getSize()).thenReturn(Long.valueOf(UPLOADED_DOCUMENT_CONTENT.length));
        when(file.getBytes()).thenThrow(new IOException("Test IO exception"));

        // when
        final var result = documentService.uploadDocument(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_EXTERNAL_DOCUMENT_ID, DUMMY_DOCUMENT_NAME, file);

        // then
        assertFailResult(result, DocumentUploadException.class, "Failed to read file: Test IO exception");
    }

    @Test
    void testUploadDocumentWhenFileIsUploadedWhenSuccessResultWithCorrectResponseIsReturned() throws NoSuchAlgorithmException {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));
        when(documentConfigurationProperties.getContentHashAlgorithm()).thenReturn(DigestAlgorithm.SHA256);

        final var documentContent = DocumentContent.builder()
                .id(1L)
                .content(UPLOADED_DOCUMENT_CONTENT)
                .build();

        when(documentContentRepository.save(any(DocumentContent.class))).thenReturn(documentContent);

        final var file = new MockMultipartFile(
                MULTIPART_FILE_FIELD_NAME,
                DUMMY_FILE_NAME,
                ContentType.APPLICATION_PDF.getMimeType(),
                UPLOADED_DOCUMENT_CONTENT
        );

        // when
        final var result = documentService.uploadDocument(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_EXTERNAL_DOCUMENT_ID, DUMMY_DOCUMENT_NAME, file);

        // then
        assertSuccessUploadResult(result);
    }

    @Test
    void testSignDocumentWhenDocumentIsNotFoundThenFailResultWithCorrectMessageIsReturned() {
        // given
        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.empty());

        final var request = new SignDocumentRequest(SIGNATURE);

        // when
        final var result = documentService.signDocument(DOCUMENT_UUID, request);

        // then
        final var expectedMessage = "Document not found for document ID: " + DOCUMENT_UUID;

        assertFailResult(result, DocumentNotFoundException.class, expectedMessage);
    }

    @Test
    void testSignDocumentWhenDocumentContentIsNotFoundThenFailResultWithCorrectMessageIsReturned() {
        // given
        final var document = Document.builder()
                .documentContentId(DOCUMENT_CONTENT_ID)
                .build();

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(DOCUMENT_CONTENT_ID)).thenReturn(Optional.empty());

        final var request = new SignDocumentRequest(SIGNATURE);

        // when
        final var result = documentService.signDocument(DOCUMENT_UUID, request);

        // then
        final var expectedMessage = "Document content not found for document ID: " + DOCUMENT_UUID;

        assertFailResult(result, DocumentNotFoundException.class, expectedMessage);
    }

    @Test
    void testSignDocumentWhenSignerIsNotFoundThenFailResultWithCorrectMessageIsReturned() {
        // given
        final var document = Document.builder()
                .documentContentId(DOCUMENT_CONTENT_ID)
                .signerId(SIGNER_ID)
                .build();

        final var documentContent = DocumentContent.builder().build();

        when(documentContentRepository.findById(DOCUMENT_CONTENT_ID)).thenReturn(Optional.of(documentContent));
        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(signerRepository.findById(SIGNER_ID)).thenReturn(Optional.empty());

        final var request = new SignDocumentRequest(SIGNATURE);

        // when
        final var result = documentService.signDocument(DOCUMENT_UUID, request);

        // then
        final var expectedMessage = "Signer not found for document ID: " + DOCUMENT_UUID;

        assertFailResult(result, SignerNotFoundException.class, expectedMessage);
    }

    @Test
    void testSignDocumentWhenSignerIsNotActiveThenFailResultWithCorrectMessageIsReturned() {
        // given
        final var document = Document.builder()
                .documentContentId(DOCUMENT_CONTENT_ID)
                .signerId(SIGNER_ID)
                .build();

        final var documentContent = DocumentContent.builder().build();

        final var signer = createSigner(SignerStatus.BLOCKED);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(DOCUMENT_CONTENT_ID)).thenReturn(Optional.of(documentContent));
        when(signerRepository.findById(SIGNER_ID)).thenReturn(Optional.of(signer));

        final var request = new SignDocumentRequest(SIGNATURE);

        // when
        final var result = documentService.signDocument(DOCUMENT_UUID, request);

        // then
        final var expectedMessage = "Signer is not active. Signer: " + DUMMY_EXTERNAL_SIGNER_ID;

        assertFailResult(result, SignDocumentException.class, expectedMessage);
    }

    @Test
    void testSignDocumentWhenDocumentHasNotWaitingStatusThenFailResultWithCorrectMessageIsReturned() {
        // given
        final var document = Document.builder()
                .documentContentId(DOCUMENT_CONTENT_ID)
                .signerId(SIGNER_ID)
                .status(DocumentStatus.REJECTED)
                .build();

        final var documentContent = DocumentContent.builder().build();

        final var signer = createSigner(SignerStatus.ACTIVE);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(DOCUMENT_CONTENT_ID)).thenReturn(Optional.of(documentContent));
        when(signerRepository.findById(SIGNER_ID)).thenReturn(Optional.of(signer));

        final var request = new SignDocumentRequest(SIGNATURE);

        // when
        final var result = documentService.signDocument(DOCUMENT_UUID, request);

        // then
        assertFailResult(result, SignDocumentException.class, "Document is not in state when it can be signed");
    }

    @Test
    void testSignDocumentWhenDocumentSignAttemptIsAfterDeadlineThenFailResultWithCorrectMessageIsReturned() {
        // given
        final var document = Document.builder()
                .timestampCreated(Instant.now().minusSeconds(120))
                .documentContentId(DOCUMENT_CONTENT_ID)
                .signerId(SIGNER_ID)
                .status(DocumentStatus.WAITING)
                .build();

        final var documentContent = DocumentContent.builder().build();

        final var signer = createSigner(SignerStatus.ACTIVE);

        final var waitingDuration = new DocumentConfigurationProperties.DocumentConfiguration();
        waitingDuration.setTimeout(WAITING_TIMEOUT);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(DOCUMENT_CONTENT_ID)).thenReturn(Optional.of(documentContent));
        when(signerRepository.findById(SIGNER_ID)).thenReturn(Optional.of(signer));
        when(documentConfigurationProperties.getWaiting()).thenReturn(waitingDuration);

        final var request = new SignDocumentRequest(SIGNATURE);

        // when
        final var result = documentService.signDocument(DOCUMENT_UUID, request);

        // then
        assertFailResult(result, SignDocumentException.class, "Document signing timeout exceeded");
    }

    @Test
    void testSignDocumentWhenSignatureIsNotValidThenFailResultWithCorrectMessageIsReturned() {
        // given
        final var document = Document.builder()
                .timestampCreated(Instant.now())
                .documentContentId(DOCUMENT_CONTENT_ID)
                .signerId(SIGNER_ID)
                .status(DocumentStatus.WAITING)
                .hash(DOCUMENT_HASH)
                .build();

        final var documentContent = DocumentContent.builder()
                .content(UPLOADED_DOCUMENT_CONTENT)
                .build();

        final var signer = createSigner(SignerStatus.ACTIVE);

        final var waitingDuration = new DocumentConfigurationProperties.DocumentConfiguration();
        waitingDuration.setTimeout(WAITING_TIMEOUT);

        final var request = new SignDocumentRequest("invalidSignature");

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(DOCUMENT_CONTENT_ID)).thenReturn(Optional.of(documentContent));
        when(signerRepository.findById(SIGNER_ID)).thenReturn(Optional.of(signer));
        when(documentConfigurationProperties.getWaiting()).thenReturn(waitingDuration);
        when(documentConfigurationProperties.getSignatureHashAlgorithm()).thenReturn(DigestAlgorithm.SHA384);

        // when
        final var result = documentService.signDocument(DOCUMENT_UUID, request);

        // then
        assertFailResult(result, SignDocumentException.class, "Invalid signature");
    }

    @Test
    void testSignDocumentWhenSignatureIsValidThenSuccessResultWithCorrectResponseIsReturned() {
        // given
        final var document = Document.builder()
                .timestampCreated(Instant.now())
                .documentContentId(DOCUMENT_CONTENT_ID)
                .signerId(SIGNER_ID)
                .status(DocumentStatus.WAITING)
                .hash(DOCUMENT_HASH)
                .documentId(DOCUMENT_UUID)
                .build();

        final var documentContent = DocumentContent.builder()
                .content(UPLOADED_DOCUMENT_CONTENT)
                .build();

        final var signer = createSigner(SignerStatus.ACTIVE);

        final var waitingDuration = new DocumentConfigurationProperties.DocumentConfiguration();
        waitingDuration.setTimeout(WAITING_TIMEOUT);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(DOCUMENT_CONTENT_ID)).thenReturn(Optional.of(documentContent));
        when(signerRepository.findById(SIGNER_ID)).thenReturn(Optional.of(signer));
        when(documentConfigurationProperties.getWaiting()).thenReturn(waitingDuration);
        when(documentConfigurationProperties.getSignatureHashAlgorithm()).thenReturn(DigestAlgorithm.SHA384);
        when(pAdESService.isValidSignatureValue(any(ToBeSigned.class), any(SignatureValue.class), any(CertificateToken.class)))
                .thenReturn(true);
        when(pAdESService.signDocument(any(InMemoryDocument.class), any(PAdESSignatureParameters.class), any(SignatureValue.class)))
                .thenReturn(new InMemoryDocument(SIGNED_DOCUMENT_CONTENT));

        prepareRequestContext();
        final var request = new SignDocumentRequest(SIGNATURE);

        // when
        final var result = documentService.signDocument(DOCUMENT_UUID, request);

        // then
        assertSuccessSignResult(result);
    }

    @Test
    void testDownloadDocumentWhenDocumentIsNotFoundThenFailResultWithCorrectMessageIsReturned() {
        // given
        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.empty());

        // when
        final var result = documentService.downloadDocument(DOCUMENT_UUID);

        // then
        assertFailResult(result, DocumentNotFoundException.class, "Document not found for document ID: " + DOCUMENT_UUID);
    }

    @Test
    void testDownloadDocumentWhenDocumentContentIsNotFoundThenFailResultWithCorrectMessageIsReturned() {
        // given
        final var document = Document.builder()
                .documentContentId(DOCUMENT_CONTENT_ID)
                .build();

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(DOCUMENT_CONTENT_ID)).thenReturn(Optional.empty());

        // when
        final var result = documentService.downloadDocument(DOCUMENT_UUID);

        // then
        assertFailResult(result, DocumentNotFoundException.class, "Document content not found for document ID: " + DOCUMENT_UUID);
    }

    @Test
    void testDownloadDocumentWhenDocumentIsNotSignedYetThenFailResultWithCorrectMessageIsReturned() {
        // given
        final var document = Document.builder()
                .documentContentId(DOCUMENT_CONTENT_ID)
                .status(DocumentStatus.WAITING)
                .build();

        final var documentContent = DocumentContent.builder().build();

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(DOCUMENT_CONTENT_ID)).thenReturn(Optional.of(documentContent));

        // when
        final var result = documentService.downloadDocument(DOCUMENT_UUID);

        // then
        assertFailResult(result, DownloadDocumentException.class, "Document is not signed yet");
    }

    @Test
    void testDownloadDocumentWhenValidRequestIsReceivedThenSuccessResultWithCorrectResponseIsReturned() throws IOException {
        // given
        final var document = Document.builder()
                .documentContentId(DOCUMENT_CONTENT_ID)
                .status(DocumentStatus.SIGNED)
                .build();

        final var documentContent = DocumentContent.builder()
                .content(SIGNED_DOCUMENT_CONTENT)
                .build();

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));
        when(documentContentRepository.findById(DOCUMENT_CONTENT_ID)).thenReturn(Optional.of(documentContent));

        // when
        final var result = documentService.downloadDocument(DOCUMENT_UUID);

        // then
        assertSuccessDownloadResult(result);
    }

    @Test
    void testRejectDocumentWhenInvalidStatusIsRequestedThenFailResultWithCorrectMessageIsReturned() {
        // given
        final var requestBody = new RejectDocumentRequest(DocumentStatus.SIGNED);

        // when
        final var result = documentService.rejectDocument(DOCUMENT_UUID, requestBody);

        // then
        assertFailResult(result, RejectDocumentException.class, "Invalid status in the request body. Expected: REJECTED, actual: SIGNED");
    }

    @Test
    void testRejectDocumentWhenDocumentIsNotFoundThenFailResultWithCorrectMessageIsReturned() {
        // given
        final var requestBody = new RejectDocumentRequest(DocumentStatus.REJECTED);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.empty());

        // when
        final var result = documentService.rejectDocument(DOCUMENT_UUID, requestBody);

        // then
        assertFailResult(result, DocumentNotFoundException.class, "Document not found for document ID: " + DOCUMENT_UUID);
    }

    @Test
    void testRejectDocumentWhenStatusIsChangedThenSuccessResultWithCorrectResponseIsReturned() {
        // given
        final var document = Document.builder()
                .documentId(DOCUMENT_UUID)
                .documentName(DUMMY_DOCUMENT_NAME)
                .fileName(DUMMY_FILE_NAME)
                .fileSize(UPLOADED_DOCUMENT_CONTENT.length)
                .hash(DOCUMENT_HASH)
                .build();

        final var requestBody = new RejectDocumentRequest(DocumentStatus.REJECTED);

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));

        // when
        final var result = documentService.rejectDocument(DOCUMENT_UUID, requestBody);

        // then
        assertRejectSuccessResult(result);
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
                .documentContentId(DOCUMENT_CONTENT_ID)
                .build();

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));

        // when / then
        assertDoesNotThrow(() -> documentService.deleteDocument(DOCUMENT_UUID));
    }

    @Test
    void testDeleteDocumentWhenDocumentExistsThenDeleteActionIsCalledOnRepositories() {
        // given
        final var document = Document.builder()
                .documentContentId(DOCUMENT_CONTENT_ID)
                .build();

        when(documentRepository.findByDocumentId(DOCUMENT_UUID)).thenReturn(Optional.of(document));

        // when
        documentService.deleteDocument(DOCUMENT_UUID);

        // then
        verify(documentRepository).delete(document);
        verify(documentContentRepository).deleteById(DOCUMENT_CONTENT_ID);
    }

    private void assertFailResult(final Try<?> result, final Class<? extends Throwable> exceptionType, final String expectedMessage) {
        assertFalse(result.isSuccess());

        final var error = result.getError();
        assertEquals(exceptionType, error.getClass());
        assertEquals(expectedMessage, error.getMessage());
    }

    private void assertSuccessUploadResult(final Try<UploadDocumentResponse> result) {
        assertTrue(result.isSuccess());

        final var response = result.getResponse();
        assertDoesNotThrow(() -> UUID.fromString(response.documentId()));
        assertEquals(DUMMY_EXTERNAL_SIGNER_ID, response.signerId());
        assertEquals(DUMMY_EXTERNAL_DOCUMENT_ID, response.externalId());
        assertEquals(DUMMY_DOCUMENT_NAME, response.name());
        assertEquals(DUMMY_FILE_NAME, response.fileName());
        assertEquals(UPLOADED_DOCUMENT_CONTENT.length, response.size());
        assertEquals(DOCUMENT_HASH, response.hash());
    }

    private void assertSuccessSignResult(final Try<SignDocumentResponse> result) {
        assertTrue(result.isSuccess());

        final var response = result.getResponse();
        assertEquals(DOCUMENT_UUID, response.documentId());

        final var expectedUri = String.format("https://signercloud.wultra.com:8080/api/v1/documents/%s/download",
                DOCUMENT_UUID);
        assertEquals(expectedUri, response.uri());
    }

    private static Signer createSigner(final SignerStatus status) {
        return Signer.builder()
                .id(SIGNER_ID)
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .certificate(CERTIFICATE)
                .status(status)
                .build();
    }

    private void prepareRequestContext() {
        final var request = new MockHttpServletRequest();
        request.setScheme("https");
        request.setServerName("signercloud.wultra.com");
        request.setServerPort(8080);

        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private void assertSuccessDownloadResult(final Try<Resource> result) throws IOException {
        assertTrue(result.isSuccess());

        final var response = result.getResponse();
        assertArrayEquals(SIGNED_DOCUMENT_CONTENT, response.getContentAsByteArray());
    }

    private void assertRejectSuccessResult(final Try<RejectDocumentResponse> result) {
        assertTrue(result.isSuccess());

        final var response = result.getResponse();
        assertEquals(DOCUMENT_UUID, response.documentId());
        assertEquals(DUMMY_DOCUMENT_NAME, response.name());
        assertEquals(DUMMY_FILE_NAME, response.filename());
        assertEquals(UPLOADED_DOCUMENT_CONTENT.length, response.size());
        assertEquals(DOCUMENT_HASH, response.hash());
    }
}
