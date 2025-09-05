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
import org.apache.commons.text.StringSubstitutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

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
    private static final String SIGN_DOCUMENT_ENDPOINT = "/api/documents/{documentId}/signature";
    private static final String DOWNLOAD_DOCUMENT_ENDPOINT = "/api/documents/{documentId}/file";
    private static final String CONTENT_TYPE = "application/pdf";
    private static final String DOCUMENT_NAME_PARAM = "name";
    private static final String EXTERNAL_DOCUMENT_ID_PARAM = "externalId";

    private static final long MILLISECONDS_DELTA = 1_000;
    private static final String ERROR_STATUS = "ERROR";

    // Signer
    private static final String EXTERNAL_SIGNER_ID_PARAM = "signerId";

    // keytool -certreq -alias myAlias -keystore keystore-ecdsa.p12 -storetype PKCS12 -file myrequest.csr -dname "CN=John Doe, O=ExampleCorp, C=US"
    private static final String CSR = "MIIBXTCB5QIBADA2MQswCQYDVQQGEwJVUzEUMBIGA1UEChMLRXhhbXBsZUNvcnAxETAPBgNVBAMTCEpvaG4gRG9lMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEQ2Z9Zsg45e2YZ89B03uhjz7LSkXuuWJW+DvT03tfdD+5bmDutM7slZzgE9fz6saNuRoBTu07qe3QkJoG1iXDOYYuTDLBp813iJOwVplFsUs11m579zSmhU31GbAtM4f/oDAwLgYJKoZIhvcNAQkOMSEwHzAdBgNVHQ4EFgQU/aKAjBfH82uqVzN6uBUK3ydJ5IYwCgYIKoZIzj0EAwMDZwAwZAIwQ8qfBDToBmyFgu+6/QUdEBHP7y6MjkNiy4KiDgGl/CNSksWarK/v6U37t6jMq1X6AjAEdYVXpTQkOOLPhJc0HE3ZpG2w14YqV1zXtTu+nfjZ4kIwfHBRL7rS+/93XPA1Hok=";
    private static final String CERTIFICATE = "MIICFDCCAZugAwIBAgIUC0O75BZKicH8bDlRUBgC8h7bTdgwCgYIKoZIzj0EAwMwFDESMBAGA1UEAwwJSXNzdWluZ0NBMB4XDTI1MDgyNzA3NTUyOVoXDTI3MDgxMTA5MTQ0NlowNjERMA8GA1UEAwwISm9obiBEb2UxFDASBgNVBAoMC0V4YW1wbGVDb3JwMQswCQYDVQQGEwJVUzB2MBAGByqGSM49AgEGBSuBBAAiA2IABENmfWbIOOXtmGfPQdN7oY8+y0pF7rliVvg709N7X3Q/uW5g7rTO7JWc4BPX8+rGjbkaAU7tO6nt0JCaBtYlwzmGLkwywafNd4iTsFaZRbFLNdZue/c0poVN9RmwLTOH/6OBizCBiDAMBgNVHRMBAf8EAjAAMB8GA1UdIwQYMBaAFJ0dk1DJP8vLqD/Dx15EMOEpmqkOMCgGA1UdJQQhMB8GCCsGAQUFBwMCBggrBgEFBQcDBAYJKoZIhvcvAQEFMB0GA1UdDgQWBBT9ooCMF8fza6pXM3q4FQrfJ0nkhjAOBgNVHQ8BAf8EBAMCBeAwCgYIKoZIzj0EAwMDZwAwZAIwaeS/siF1g5vbaNXrnQM9xJOQmUG92HyNOCTKh/x1PA9b/VwtpodSjkIOiOxJQ56aAjBQit9XczUVNp5qGdrLO3Ac730VokRvphNBtupJbdnkpywejktZi00LM8MsuZA7Piw=";

    // Document
    private static final String DOCUMENT_UUID = "3f6a8c50-4e02-4d3f-8f5c-6b92a1e5b9d7";
    private static final String DUMMY_EXTERNAL_SIGNER_ID = "dummyExternalSignerId";
    private static final String DUMMY_EXTERNAL_DOCUMENT_ID = "dummyExternalDocumentId";
    private static final String DUMMY_DOCUMENT_NAME = "dummyDocumentName";

    // shasum -a 256 input.pdf | awk '{print $1}' | xxd -r -p | base64
    private static final String HASH = "j0LPvNkxjaHXp4rdyChWunro9pIdThrlTyDbMKIdnfk=";

    // echo "value_of_hash" | base64 --decode > hash.bin
    // openssl pkcs12 -in keystore-ecdsa.p12 -nocerts -nodes -out mykey.pem
    // openssl dgst -sha384 -sign mykey.pem -out signature.bin hash.bin
    // base64 < signature.bin
    private static final String SIGNATURE = "MGQCMBawZBUmDeQOFGo9AiruqAN8NAH7apayQoPVEgCvOpYcfkArSehUL8EHs8iFVmn3ZAIwZOcJgEbrwpGCBl05hR0DeBtaJLTTIaYNae70csEku+AUgr9AUyWqjGaB/Vvbt+RQ";
    private static final String FILENAME = "input.pdf";
    private static final int UPLOADED_FILE_SIZE = 7757;
    private static final int SIGNED_FILE_SIZE = 27780;

    // Config
    private static final long DOCUMENT_WAITING_TIMEOUT_SECONDS = 3600;

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

    private byte[] uploadedDocumentContent;
    private byte[] signedDocumentContent;

    @BeforeEach
    void setUp() throws IOException {
        uploadedDocumentContent = new ClassPathResource("input.pdf").getContentAsByteArray();
        signedDocumentContent = new ClassPathResource("input_signed.pdf").getContentAsByteArray();
    }

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
    void testUploadWhenSignerIsNotFoundThen400WithCorrectResponseIsReturned() throws Exception {
        // given
        final var file = loadFile(CONTENT_TYPE);

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
        assertErrorResponse(responseBody, ErrorCode.ERROR_RESOURCE_NOT_FOUND, "Signer not found for external signer ID: dummyExternalSignerId");
    }

    @Test
    void testUploadWhenOperationIsSuccessfulThen200WithCorrectResponseIsReturned() throws Exception {
        // given
        createSignerInDatabase(SignerStatus.ACTIVE);

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
        createSignerInDatabase(SignerStatus.ACTIVE);

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
        assertUploadedDocument(document, responseBody.documentId());
    }

    @Test
    void testSignWhenDocumentIsNotFoundThen400WithCorrectResponseIsReturned() throws Exception {
        // given
        final var request = new SignDocumentRequest(SIGNATURE);

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.post(SIGN_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);

        assertErrorResponse(responseBody, ErrorCode.ERROR_RESOURCE_NOT_FOUND, "Document not found for document ID: " + DOCUMENT_UUID);
    }

    @Test
    void testSignWhenSignerIsNotActiveThen400WithCorrectResponseIsReturned() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.BLOCKED);
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.WAITING, Instant.now());

        final var request = new SignDocumentRequest(SIGNATURE);

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.post(SIGN_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);

        assertErrorResponse(responseBody, ErrorCode.ERROR_GENERIC, "Signer is not active. Signer: " + DUMMY_EXTERNAL_SIGNER_ID);
    }

    @Test
    void testSignWhenDocumentIsNotInWaitingStateThen400WithCorrectResponseIsReturned() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.SIGNED, Instant.now());

        final var request = new SignDocumentRequest(SIGNATURE);

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.post(SIGN_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);

        assertErrorResponse(responseBody, ErrorCode.ERROR_GENERIC, "Document is not in state when it can be signed");
    }

    @Test
    void testSignWhenDocumentSignAttemptIsAfterDeadlineThen400WithCorrectResponseIsReturned() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        final var creationTimeAfterWaitingTimeout = Instant.now().minusSeconds(DOCUMENT_WAITING_TIMEOUT_SECONDS + 60);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.WAITING, creationTimeAfterWaitingTimeout);

        final var request = new SignDocumentRequest(SIGNATURE);

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.post(SIGN_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);

        assertErrorResponse(responseBody, ErrorCode.ERROR_GENERIC, "Document signing timeout exceeded");
    }

    @Test
    void testSignWhenSignatureIsInvalidThen400WithCorrectResponseIsReturned() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.WAITING, Instant.now());

        final var request = new SignDocumentRequest("invalidSignature");

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.post(SIGN_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);

        assertErrorResponse(responseBody, ErrorCode.ERROR_GENERIC, "Invalid signature");
    }

    @Test
    void testSignWhenSignatureIsValidThen200WithCorrectResponseIsReturned() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.WAITING, Instant.now());

        final var request = new SignDocumentRequest(SIGNATURE);

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.post(SIGN_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-Host", "signercloud.wultra.com")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Port", "8080")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), SignDocumentResponse.class);

        assertSignResponse(responseBody);
    }

    @Test
    void testSignWhenSignatureIsValidThenDocumentIsUpdatedInDatabaseWithCorrectValues() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        final var documentId = createDocumentInDatabase(signerId, documentContentId, DocumentStatus.WAITING, Instant.now().minusSeconds(30));

        final var request = new SignDocumentRequest(SIGNATURE);

        // when
        mockMvc.perform(MockMvcRequestBuilders.post(SIGN_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        assertSignedDocument(documentId, signerId, documentContentId);
    }

    @Test
    void testDownloadWhenDocumentIsNotFoundThen400WithCorrectResponseIsReturned() throws Exception {
        // given
        // -

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.get(DOWNLOAD_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);

        assertErrorResponse(responseBody, ErrorCode.ERROR_RESOURCE_NOT_FOUND, "Document not found for document ID: " + DOCUMENT_UUID);
    }

    @Test
    void testDownloadWhenDocumentIsNotSignedYetThen400WithCorrectResponseIsReturned() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.WAITING, Instant.now());

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.get(DOWNLOAD_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);

        assertErrorResponse(responseBody, ErrorCode.ERROR_GENERIC, "Document is not signed yet");
    }

    @Test
    void testDownloadWhenInvalidHeaderRangeIsProvidedThen400WithCorrectResponseIsReturned() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.SIGNED, Instant.now());

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.get(DOWNLOAD_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .header("Range", "bytes=a-100")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);

        assertErrorResponse(responseBody, ErrorCode.ERROR_GENERIC, "Invalid range header: bytes=a-100 Reason: Error at index 0 in: \"a\"");
    }

    @Test
    void testDownloadWhenRangeHeaderIsNotProvidedThen200WithFullContentIsReturned() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(signedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.SIGNED, Instant.now());

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.get(DOWNLOAD_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // then
        assertDownloadResponseWithFullContent(result.getResponse());
    }

    @Test
    void testDownloadWhenHeaderWithSingleRangeIsProvidedThen206WithSinglePartIsReturned() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(signedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.SIGNED, Instant.now());

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.get(DOWNLOAD_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Range", "bytes=0-99"))
                .andExpect(status().isPartialContent())
                .andReturn();

        // then
        assertDownloadResponseWithSinglePart(result.getResponse());
    }

    @Test
    void testDownloadWhenHeaderWithMultipleRangesIsProvidedThen206WithAllPartsIsReturned() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(signedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.SIGNED, Instant.now());

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.get(DOWNLOAD_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Range", "bytes=0-99,200-299"))
                .andExpect(status().isPartialContent())
                .andReturn();

        // then
        assertDownloadResponseWithMultipleParts(result.getResponse());
    }

    private MockMultipartFile loadFile(final String contentType) {
        return new MockMultipartFile(
                "file",
                FILENAME,
                contentType,
                uploadedDocumentContent
        );
    }

    private void assertErrorResponse(final ErrorResponse errorResponse, final ErrorCode expectedCode, final String expectedMessage) {
        assertEquals(ERROR_STATUS, errorResponse.status());

        final var responseObject = errorResponse.responseObject();
        assertEquals(expectedCode, responseObject.code());
        assertEquals(expectedMessage, responseObject.message());
    }

    private long createSignerInDatabase(final SignerStatus status) {
        final var signer = Signer.builder()
                .timestampCreated(Instant.now())
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .userId("dummyUserId")
                .csr(CSR)
                .certificate(CERTIFICATE)
                .timestampCertificateExpiration(Instant.now())
                .status(status)
                .build();

        final var savedSigner = signerRepository.save(signer);
        return savedSigner.getId();
    }

    private long createDocumentContentInDatabase(final byte[] content) {
        final var documentContent = DocumentContent.builder()
                .content(content)
                .build();

        final var savedDocumentContent = documentContentRepository.save(documentContent);
        return savedDocumentContent.getId();
    }

    private long createDocumentInDatabase(final long signerId, final long documentContentId, final DocumentStatus status, final Instant creationTime) {
        final var document = Document.builder()
                .timestampCreated(creationTime)
                .documentId(DOCUMENT_UUID)
                .signerId(signerId)
                .externalId(DUMMY_EXTERNAL_DOCUMENT_ID)
                .documentName(DUMMY_DOCUMENT_NAME)
                .fileName(FILENAME)
                .fileSize(UPLOADED_FILE_SIZE)
                .hash(HASH)
                .status(status)
                .documentContentId(documentContentId)
                .build();

        final var savedDocument = documentRepository.save(document);
        return savedDocument.getId();
    }

    private void assertUploadResponse(final UploadDocumentResponse response) {
        assertDoesNotThrow(() -> UUID.fromString(response.documentId()));
        assertEquals(DUMMY_EXTERNAL_SIGNER_ID, response.signerId());
        assertEquals(DUMMY_EXTERNAL_DOCUMENT_ID, response.externalId());
        assertEquals(DUMMY_DOCUMENT_NAME, response.name());
        assertEquals(FILENAME, response.fileName());
        assertEquals(UPLOADED_FILE_SIZE, response.size());
        assertEquals(HASH, response.hash());
    }

    private void assertUploadedDocument(final Document document, final String expectedDocumentId) throws IOException {
        assertNotEquals(0, document.getId());
        assertEquals(Instant.now().toEpochMilli(), document.getTimestampCreated().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(expectedDocumentId, document.getDocumentId());
        assertTrue(signerRepository.existsById(document.getSignerId()));
        assertEquals(DUMMY_EXTERNAL_DOCUMENT_ID, document.getExternalId());
        assertEquals(DUMMY_DOCUMENT_NAME, document.getDocumentName());
        assertEquals(FILENAME, document.getFileName());
        assertEquals(UPLOADED_FILE_SIZE, document.getFileSize());
        assertEquals(HASH, document.getHash());
        assertEquals(DocumentStatus.WAITING, document.getStatus());
        assertNull(document.getSignature());

        final var documentContent = documentContentRepository.findById(document.getDocumentContentId()).orElseThrow();
        assertArrayEquals(uploadedDocumentContent, documentContent.getContent());
    }

    private void assertSignResponse(final SignDocumentResponse response) {
        assertEquals(DOCUMENT_UUID, response.documentId());

        final var expectedUri = String.format("https://signercloud.wultra.com:8080/api/v1/documents/%s/download", DOCUMENT_UUID);
        assertEquals(expectedUri, response.uri());
    }

    private void assertSignedDocument(final long documentId, final long signerId, final long documentContentId) {
        final var document = documentRepository.findById(documentId).orElseThrow();
        assertEquals(Instant.now().minusSeconds(30).toEpochMilli(), document.getTimestampCreated().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(Instant.now().toEpochMilli(), document.getTimestampLastUpdated().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(DOCUMENT_UUID, document.getDocumentId());
        assertEquals(signerId, document.getSignerId());
        assertEquals(DUMMY_EXTERNAL_DOCUMENT_ID, document.getExternalId());
        assertEquals(DUMMY_DOCUMENT_NAME, document.getDocumentName());
        assertEquals(FILENAME, document.getFileName());
        assertEquals(SIGNED_FILE_SIZE, document.getFileSize());
        assertEquals(documentContentId, document.getDocumentContentId());
        assertEquals(HASH, document.getHash());
        assertEquals(DocumentStatus.SIGNED, document.getStatus());
        assertEquals(SIGNATURE, document.getSignature());

        final var documentContent = documentContentRepository.findById(document.getDocumentContentId()).orElseThrow();
        final var fileContent = documentContent.getContent();
        assertTrue(UPLOADED_FILE_SIZE < fileContent.length);
    }

    private void assertDownloadResponseWithFullContent(final MockHttpServletResponse response) {
        assertEquals(signedDocumentContent.length, response.getContentLength());
        assertEquals("bytes", response.getHeader("Accept-Ranges"));
        assertNull(response.getHeader("Content-Range"));
        assertEquals(MediaType.APPLICATION_PDF_VALUE, response.getContentType());

        final var responseBody = response.getContentAsByteArray();
        assertArrayEquals(signedDocumentContent, responseBody);
    }

    private void assertDownloadResponseWithSinglePart(final MockHttpServletResponse response) {
        assertEquals(100, response.getContentLength());
        assertEquals("bytes", response.getHeader("Accept-Ranges"));
        assertEquals("bytes 0-99/27780", response.getHeader("Content-Range"));
        assertEquals(MediaType.APPLICATION_PDF_VALUE, response.getContentType());

        final var expectedBody = Arrays.copyOfRange(signedDocumentContent, 0, 100);
        final var responseBody = response.getContentAsByteArray();
        assertArrayEquals(expectedBody, responseBody);
    }

    private void assertDownloadResponseWithMultipleParts(final MockHttpServletResponse response) {
        final var boundarySeparator = findSeparator(response.getContentType());

        final var actualBody = new String(response.getContentAsByteArray(), StandardCharsets.ISO_8859_1);
        final var expectedBody = buildExpectedMultiRangesBody(boundarySeparator);
        assertEquals(expectedBody, actualBody);

        assertEquals(expectedBody.length(), response.getContentLength());
        assertEquals("bytes", response.getHeader("Accept-Ranges"));
        assertNull(response.getHeader("Content-Range"));
    }

    private static String findSeparator(final String text) {
        final var pattern = Pattern.compile("multipart/byteranges; boundary=(MULTIPART_BYTERANGES_\\d+)");
        final var matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return fail("Separator for boundary not found in header");
    }

    private String buildExpectedMultiRangesBody(final String separator) {
        final var template = """
                --${boundary}
                Content-Type: ${contentType}
                Content-Range: bytes 0-99/${totalLength}
                
                ${range1}
                --${boundary}
                Content-Type: ${contentType}
                Content-Range: bytes 200-299/${totalLength}
                
                ${range2}
                --${boundary}--
                """;

        final var values = Map.of(
                "boundary", separator,
                "contentType", MediaType.APPLICATION_PDF,
                "totalLength", signedDocumentContent.length,
                "range1", new String(Arrays.copyOfRange(signedDocumentContent, 0, 100), StandardCharsets.ISO_8859_1),
                "range2", new String(Arrays.copyOfRange(signedDocumentContent, 200, 300), StandardCharsets.ISO_8859_1)
        );

        return StringSubstitutor.replace(template, values);
    }
}