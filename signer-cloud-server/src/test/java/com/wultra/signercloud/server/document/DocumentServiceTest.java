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
import com.wultra.signercloud.server.signer.SignerRepository;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DocumentService}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final long DUMMY_SIGNER_ID = 1L;
    private static final String DUMMY_EXTERNAL_SIGNER_ID = "dummyExternalSignerId";

    private static final String DUMMY_EXTERNAL_DOCUMENT_ID = "dummyExternalDocumentId";
    private static final String DUMMY_DOCUMENT_NAME = "dummyDocumentName";
    private static final String DUMMY_FILE_NAME = "dummyFileName.pdf";
    private static final String DUMMY_DOCUMENT_CONTENT = "dummyDocumentContent";
    private static final String MULTIPART_FILE_FIELD_NAME = "content";

    private static final String EXPECTED_HASH = "e5416a420beede017508e5c172f13ed9344e9a3ac89e3ae9d49c559f3f97b6d5";

    private Signer signer;

    @Mock
    private SignerRepository signerRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentContentRepository documentContentRepository;

    @InjectMocks
    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        signer = Signer.builder()
                .id(DUMMY_SIGNER_ID)
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .build();
    }

    @Test
    void testUploadDocumentWhenUnsupportedFileTypeIsReceivedThenFailResultWithCorrectMessageIsReturned() throws NoSuchAlgorithmException {
        // given
        final var file = new MockMultipartFile(
                MULTIPART_FILE_FIELD_NAME,
                DUMMY_FILE_NAME,
                ContentType.IMAGE_JPEG.getMimeType(),
                DUMMY_DOCUMENT_CONTENT.getBytes()
        );

        // when
        final var result = documentService.uploadDocument(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_EXTERNAL_DOCUMENT_ID, DUMMY_DOCUMENT_NAME, file);

        // then
        assertFailResult(result, "Unsupported content type: image/jpeg");
    }

    @Test
    void testUploadDocumentWhenSignerIsNotFoundThenFailResultWithCorrectMessageIsReturned() throws NoSuchAlgorithmException {
        // given
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.empty());

        final var file = new MockMultipartFile(
                MULTIPART_FILE_FIELD_NAME,
                DUMMY_FILE_NAME,
                ContentType.APPLICATION_PDF.getMimeType(),
                DUMMY_DOCUMENT_CONTENT.getBytes()
        );

        // when
        final var result = documentService.uploadDocument(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_EXTERNAL_DOCUMENT_ID, DUMMY_DOCUMENT_NAME, file);

        // then
        assertFailResult(result, "Signer not found for external signer ID: dummyExternalSignerId");
    }

    @Test
    void testUploadDocumentWhenFileIsTooLargeThenFailResultWithCorrectMessageIsReturned() throws NoSuchAlgorithmException {
        // given
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        final var file = Mockito.mock(MultipartFile.class);
        when(file.getContentType()).thenReturn(ContentType.APPLICATION_PDF.getMimeType());
        when(file.getSize()).thenReturn(1L + Integer.MAX_VALUE);

        // when
        final var result = documentService.uploadDocument(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_EXTERNAL_DOCUMENT_ID, DUMMY_DOCUMENT_NAME, file);

        // then
        assertFailResult(result, "File is too large. Size: 2147483648");
    }

    @Test
    void testUploadDocumentWhenFileContentCanNotBeReadThenFailResultWithCorrectMessageIsReturned() throws NoSuchAlgorithmException, IOException {
        // given
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        final var file = Mockito.mock(MultipartFile.class);
        when(file.getContentType()).thenReturn(ContentType.APPLICATION_PDF.getMimeType());
        when(file.getSize()).thenReturn(Long.valueOf(DUMMY_DOCUMENT_CONTENT.length()));
        when(file.getBytes()).thenThrow(new IOException("Test IO exception"));

        // when
        final var result = documentService.uploadDocument(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_EXTERNAL_DOCUMENT_ID, DUMMY_DOCUMENT_NAME, file);

        // then
        assertFailResult(result, "Failed to read file: Test IO exception");
    }

    @Test
    void testUploadDocumentWhenFileIsUploadedWhenSuccessResultWithCorrectResponseIsReturned() throws NoSuchAlgorithmException {
        // given
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        final var documentContent = DocumentContent.builder()
                .id(1L)
                .content(DUMMY_DOCUMENT_CONTENT.getBytes())
                .build();

        when(documentContentRepository.save(any(DocumentContent.class))).thenReturn(documentContent);

        final var file = new MockMultipartFile(
                MULTIPART_FILE_FIELD_NAME,
                DUMMY_FILE_NAME,
                ContentType.APPLICATION_PDF.getMimeType(),
                DUMMY_DOCUMENT_CONTENT.getBytes()
        );

        // when
        final var result = documentService.uploadDocument(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_EXTERNAL_DOCUMENT_ID, DUMMY_DOCUMENT_NAME, file);

        // then
        assertSuccessResult(result);
    }

    private void assertFailResult(final Try<UploadDocumentResponse> result, final String expectedMessage) {
        assertFalse(result.isSuccess());
        assertEquals(expectedMessage, result.getError().getMessage());
    }

    private void assertSuccessResult(final Try<UploadDocumentResponse> result) {
        assertTrue(result.isSuccess());

        final var response = result.getResponse();
        assertDoesNotThrow(() -> UUID.fromString(response.documentId()));
        assertEquals(DUMMY_EXTERNAL_SIGNER_ID, response.signerId());
        assertEquals(DUMMY_EXTERNAL_DOCUMENT_ID, response.externalId());
        assertEquals(DUMMY_DOCUMENT_NAME, response.name());
        assertEquals(DUMMY_FILE_NAME, response.fileName());
        assertEquals(DUMMY_DOCUMENT_CONTENT.length(), response.size());
        assertEquals(EXPECTED_HASH, response.hash());
    }
}
