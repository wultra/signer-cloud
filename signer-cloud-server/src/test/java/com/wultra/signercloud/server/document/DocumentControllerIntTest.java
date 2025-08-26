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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wultra.signercloud.server.restapi.ErrorCode;
import com.wultra.signercloud.server.restapi.ErrorResponse;
import com.wultra.signercloud.server.signer.Signer;
import com.wultra.signercloud.server.signer.SignerRepository;
import com.wultra.signercloud.server.signer.SignerStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link DocumentController}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@SpringBootTest(properties = {
        "ejbca.rest-client.key-store-password=testPassword",
        "ejbca.rest-client.key-alias=testAlias",
        "ejbca.rest-client.key-password=testKeyPassword"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class DocumentControllerIntTest {

    private static final String UPLOAD_DOCUMENT_ENDPOINT = "/api/documents";

    private static final String EXTERNAL_SIGNER_ID_PARAM = "signerId";
    private static final String EXTERNAL_DOCUMENT_ID_PARAM = "externalId";
    private static final String DOCUMENT_NAME_PARAM = "name";

    private static final String DUMMY_EXTERNAL_SIGNER_ID = "dummyExternalSignerId";
    private static final String DUMMY_EXTERNAL_DOCUMENT_ID = "dummyExternalDocumentId";
    private static final String DUMMY_DOCUMENT_NAME = "dummyDocumentName";
    private static final String CONTENT_TYPE = "application/pdf";
    private static final String FILENAME = "input.pdf";
    private static final int FILE_SIZE = 7757;
    private static final String HASH = "8f42cfbcd9318da1d7a78addc82856ba7ae8f6921d4e1ae54f20db30a21d9df9";
    private static final Path FILE_PATH = Path.of("./src/test/resources/input.pdf");

    private static final String ERROR_STATUS = "ERROR";

    private static final long MILLISECONDS_DELTA = 1_000;

    @Autowired
    private SignerRepository signerRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentContentRepository documentContentRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void tearDown() {
        documentRepository.deleteAll();
        signerRepository.deleteAll();
        documentContentRepository.deleteAll();
    }

    @Test
    void testUploadWhenUnsupportedFileTypeIsUploadedThen400WithCorrectResponseIsReturned() throws Exception {
        // given
        final var file = loadFile("image/png");

        // when
        final var result = mockMvc.perform(multipart(UPLOAD_DOCUMENT_ENDPOINT)
                        .file(file)
                        .param(EXTERNAL_SIGNER_ID_PARAM, DUMMY_EXTERNAL_SIGNER_ID)
                        .param(EXTERNAL_DOCUMENT_ID_PARAM, DUMMY_EXTERNAL_DOCUMENT_ID)
                        .param(DOCUMENT_NAME_PARAM, DUMMY_DOCUMENT_NAME))
                .andExpect(status().isBadRequest())
                .andReturn();

        // when
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);
        assertErrorResponse(responseBody, ErrorCode.ERROR_GENERIC, "Unsupported content type: image/png");
    }

    @Test
    void testUploadWhenSignerIsNotFoundThen404WithCorrectResponseIsReturned() throws Exception {
        // given
        final var file = loadFile(CONTENT_TYPE);

        // when
        final var result = mockMvc.perform(multipart(UPLOAD_DOCUMENT_ENDPOINT)
                        .file(file)
                        .param(EXTERNAL_SIGNER_ID_PARAM, DUMMY_EXTERNAL_SIGNER_ID)
                        .param(EXTERNAL_DOCUMENT_ID_PARAM, DUMMY_EXTERNAL_DOCUMENT_ID)
                        .param(DOCUMENT_NAME_PARAM, DUMMY_DOCUMENT_NAME))
                .andExpect(status().isNotFound())
                .andReturn();

        // when
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);
        assertErrorResponse(responseBody, ErrorCode.ERROR_RESOURCE_NOT_FOUND, "Signer not found for external signer ID: dummyExternalSignerId");
    }

    @Test
    void testUploadWhenOperationIsSuccessfulThen200WithCorrectResponseIsReturned() throws Exception {
        // given
        createSignerInDatabase();

        final var file = loadFile(CONTENT_TYPE);

        // when
        final var result = mockMvc.perform(multipart(UPLOAD_DOCUMENT_ENDPOINT)
                        .file(file)
                        .param(EXTERNAL_SIGNER_ID_PARAM, DUMMY_EXTERNAL_SIGNER_ID)
                        .param(EXTERNAL_DOCUMENT_ID_PARAM, DUMMY_EXTERNAL_DOCUMENT_ID)
                        .param(DOCUMENT_NAME_PARAM, DUMMY_DOCUMENT_NAME))
                .andExpect(status().isOk())
                .andReturn();

        // when
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), UploadDocumentResponse.class);
        assertUploadResponse(responseBody);
    }

    @Test
    void testUploadWhenOperationIsSuccessfulThenCorrectValuesAreStoredIntoDatabase() throws Exception {
        // given
        createSignerInDatabase();

        final var file = loadFile(CONTENT_TYPE);

        // when
        final var result = mockMvc.perform(multipart(UPLOAD_DOCUMENT_ENDPOINT)
                        .file(file)
                        .param(EXTERNAL_SIGNER_ID_PARAM, DUMMY_EXTERNAL_SIGNER_ID)
                        .param(EXTERNAL_DOCUMENT_ID_PARAM, DUMMY_EXTERNAL_DOCUMENT_ID)
                        .param(DOCUMENT_NAME_PARAM, DUMMY_DOCUMENT_NAME))
                .andExpect(status().isOk())
                .andReturn();

        // when
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), UploadDocumentResponse.class);

        final var document = documentRepository.findAll().iterator().next();
        assertDocument(document, responseBody.documentId());
    }

    private MockMultipartFile loadFile(final String contentType) throws IOException {
        return new MockMultipartFile(
                "file",
                FILENAME,
                contentType,
                Files.readAllBytes(FILE_PATH)
        );
    }

    private void assertErrorResponse(final ErrorResponse errorResponse, final ErrorCode expectedCode, final String expectedMessage) {
        assertEquals(ERROR_STATUS, errorResponse.status());

        final var responseObject = errorResponse.responseObject();
        assertEquals(expectedCode, responseObject.code());
        assertEquals(expectedMessage, responseObject.message());
    }

    private void createSignerInDatabase() {
        final var signer = Signer.builder()
                .timestampCreated(Instant.now())
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .userId("dummyUserId")
                .csr("dummyCsr")
                .certificate("dummyCertificate")
                .timestampCertificateExpiration(Instant.now())
                .status(SignerStatus.ACTIVE)
                .build();

        signerRepository.save(signer);
    }

    private void assertUploadResponse(final UploadDocumentResponse response) {
        assertDoesNotThrow(() -> UUID.fromString(response.documentId()));
        assertEquals(DUMMY_EXTERNAL_SIGNER_ID, response.signerId());
        assertEquals(DUMMY_EXTERNAL_DOCUMENT_ID, response.externalId());
        assertEquals(DUMMY_DOCUMENT_NAME, response.name());
        assertEquals(FILENAME, response.fileName());
        assertEquals(FILE_SIZE, response.size());
        assertEquals(HASH, response.hash());
    }

    private void assertDocument(final Document document, final String expectedDocumentId) throws IOException {
        assertNotEquals(0, document.getId());
        assertEquals(Instant.now().toEpochMilli(), document.getTimestampCreated().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(expectedDocumentId, document.getDocumentId());
        assertTrue(signerRepository.existsById(document.getSignerId()));
        assertEquals(DUMMY_EXTERNAL_DOCUMENT_ID, document.getExternalId());
        assertEquals(DUMMY_DOCUMENT_NAME, document.getDocumentName());
        assertEquals(FILENAME, document.getFileName());
        assertEquals(FILE_SIZE, document.getFileSize());
        assertEquals(HASH, document.getHash());
        assertEquals(DocumentStatus.WAITING, document.getStatus());
        assertNull(document.getSignature());

        final var documentContent = documentContentRepository.findById(document.getDocumentContentId()).orElseThrow();
        assertArrayEquals(Files.readAllBytes(FILE_PATH), documentContent.getContent());
    }
}