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
import com.wultra.signercloud.server.IntTestUtils;
import com.wultra.signercloud.server.restapi.ErrorCode;
import com.wultra.signercloud.server.restapi.ErrorResponse;
import com.wultra.signercloud.server.signer.Signer;
import com.wultra.signercloud.server.signer.SignerRepository;
import com.wultra.signercloud.server.signer.SignerStatus;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.validation.PDFDocumentValidator;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import org.apache.commons.text.StringSubstitutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.mock.web.MockPart;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        "ejbca.rest-client.key-password=testKeyPassword",
        "signer-cloud.server.document.waiting.timeout=",
        "signer-cloud.server.pades.tsa-url=https://freetsa.org/tsr"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class DocumentControllerIntTest {

    private static final String UPLOAD_DOCUMENT_ENDPOINT = "/documents";
    private static final String SIGN_DOCUMENT_ENDPOINT = "/documents/{documentId}/signature";
    private static final String DOWNLOAD_DOCUMENT_ENDPOINT = "/documents/{documentId}/file";
    private static final String REJECT_DOCUMENT_ENDPOINT = "/documents/{documentId}";
    private static final String DELETE_DOCUMENT_ENDPOINT = "/documents/{documentId}";
    private static final String CONTENT_TYPE = "application/pdf";
    private static final String DOCUMENT_NAME_PARAM = "name";
    private static final String EXTERNAL_DOCUMENT_ID_PARAM = "externalId";

    private static final long MILLISECONDS_DELTA = 1_000;
    private static final String ERROR_STATUS = "ERROR";

    // Signer
    private static final String EXTERNAL_SIGNER_ID_PARAM = "signerId";

    // Document
    private static final String DOCUMENT_UUID = "75142815-7adc-4962-afd2-1e498d38b90d";
    private static final String EXTERNAL_SIGNER_ID = "6fdbc9a0-7dd8-4891-adcf-ebceac188e13";
    private static final String EXTERNAL_DOCUMENT_ID = "external-document-id";
    private static final String DOCUMENT_NAME = "Document Test";

    private static final String FILENAME = "input.pdf";
    private static final int UPLOADED_FILE_SIZE = 7757;
    private static final int SIGNED_FILE_SIZE = 27780;

    private static String userCsrDerBase64;
    private static String userCertificateDerBase64;
    private static List<String> userCertificateChainBase64;

    private static Instant documentTimestampCreated;
    private static String documentHashBase64;
    private static String documentSignatureBase64;

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

    private static byte[] uploadedDocumentContent;
    private static byte[] signedDocumentContent;
    private static String signatureImageBase64;

    @BeforeAll
    static void setUp() throws Exception {
        final var testResources = IntTestUtils.prepare();

        final var signerResources = testResources.signerResources();
        userCsrDerBase64 = Base64.getEncoder().encodeToString(signerResources.userCsrDer());
        userCertificateDerBase64 = Base64.getEncoder().encodeToString(signerResources.userCertificate().getEncoded());
        userCertificateChainBase64 = signerResources.userCertificateChain().stream()
                .map(c -> {
                    try {
                        return Base64.getEncoder().encodeToString(c.getEncoded());
                    } catch (final CertificateEncodingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        final var documentResources = testResources.documentResources();
        uploadedDocumentContent = documentResources.unsignedContent();
        documentTimestampCreated = documentResources.timestampCreated();
        documentHashBase64 = Base64.getEncoder().encodeToString(documentResources.hashSha256());
        documentSignatureBase64 = Base64.getEncoder().encodeToString(documentResources.signatureSha256());
        signedDocumentContent = documentResources.signedContentSha256();

        signatureImageBase64 = Base64.getEncoder().encodeToString(
                new ClassPathResource("signature-pen.png").getContentAsByteArray()
        );
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
                        .param(EXTERNAL_SIGNER_ID_PARAM, EXTERNAL_SIGNER_ID)
                        .param(EXTERNAL_DOCUMENT_ID_PARAM, EXTERNAL_DOCUMENT_ID)
                        .param(DOCUMENT_NAME_PARAM, DOCUMENT_NAME))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);
        assertErrorResponse(responseBody, ErrorCode.DOCUMENT_UPLOAD_ERROR, "Unsupported content type: image/png");
    }

    @Test
    void testUploadWhenSignerIsNotFoundThen400WithCorrectResponseIsReturned() throws Exception {
        // given
        final var file = loadFile(CONTENT_TYPE);

        // when
        final var result = mockMvc.perform(multipart(UPLOAD_DOCUMENT_ENDPOINT)
                        .file(file)
                        .param(EXTERNAL_SIGNER_ID_PARAM, EXTERNAL_SIGNER_ID)
                        .param(EXTERNAL_DOCUMENT_ID_PARAM, EXTERNAL_DOCUMENT_ID)
                        .param(DOCUMENT_NAME_PARAM, DOCUMENT_NAME))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);
        assertErrorResponse(responseBody, ErrorCode.ERROR_RESOURCE_NOT_FOUND, "Signer with ID %s not found".formatted(EXTERNAL_SIGNER_ID));
    }

    @Test
    void testUploadWhenOperationIsSuccessfulThen200WithCorrectResponseIsReturned() throws Exception {
        // given
        createSignerInDatabase(SignerStatus.ACTIVE);

        final var file = loadFile(CONTENT_TYPE);

        // when
        final var result = mockMvc.perform(multipart(UPLOAD_DOCUMENT_ENDPOINT)
                        .file(file)
                        .param(EXTERNAL_SIGNER_ID_PARAM, EXTERNAL_SIGNER_ID)
                        .param(EXTERNAL_DOCUMENT_ID_PARAM, EXTERNAL_DOCUMENT_ID)
                        .param(DOCUMENT_NAME_PARAM, DOCUMENT_NAME))
                .andExpect(status().isOk())
                .andReturn();

        // then
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
                        .param(EXTERNAL_SIGNER_ID_PARAM, EXTERNAL_SIGNER_ID)
                        .param(EXTERNAL_DOCUMENT_ID_PARAM, EXTERNAL_DOCUMENT_ID)
                        .param(DOCUMENT_NAME_PARAM, DOCUMENT_NAME))
                .andExpect(status().isOk())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), UploadDocumentResponse.class);

        final var document = documentRepository.findAll().iterator().next();
        assertUploadedDocument(document, responseBody.documentId());
    }

    @Test
    void testUploadWhenVisualSignatureIsProvidedThenItIsStoredIntoDatabase() throws Exception {
        // given
        createSignerInDatabase(SignerStatus.ACTIVE);

        final var file = loadFile(CONTENT_TYPE);
        final var visualSignature = createVisualSignaturePart();

        // when
        mockMvc.perform(multipart(UPLOAD_DOCUMENT_ENDPOINT)
                        .file(file)
                        .part(visualSignature)
                        .param(EXTERNAL_SIGNER_ID_PARAM, EXTERNAL_SIGNER_ID)
                        .param(EXTERNAL_DOCUMENT_ID_PARAM, EXTERNAL_DOCUMENT_ID)
                        .param(DOCUMENT_NAME_PARAM, DOCUMENT_NAME))
                .andExpect(status().isOk())
                .andReturn();

        // then
        final var document = documentRepository.findAll().iterator().next();
        assertVisualSignature(document.getVisualSignature());
    }

    @Test
    void testSignWhenDocumentIsNotFoundThen400WithCorrectResponseIsReturned() throws Exception {
        // given
        final var request = new SignDocumentRequest(documentSignatureBase64, null);

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.post(SIGN_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);

        assertErrorResponse(responseBody, ErrorCode.ERROR_RESOURCE_NOT_FOUND, "Document with ID %s not found".formatted(DOCUMENT_UUID));
    }

    @Test
    void testSignWhenSignerIsNotActiveThen400WithCorrectResponseIsReturned() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.BLOCKED);
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.WAITING, Instant.now());

        final var request = new SignDocumentRequest(documentSignatureBase64, null);

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.post(SIGN_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);

        assertErrorResponse(responseBody, ErrorCode.ILLEGAL_OPERATION_ERROR, "Signer is not active. Signer: " + EXTERNAL_SIGNER_ID);
    }

    @Test
    void testSignWhenDocumentIsNotInWaitingStateThen400WithCorrectResponseIsReturned() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.SIGNED, Instant.now());

        final var request = new SignDocumentRequest(documentSignatureBase64, null);

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.post(SIGN_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);

        assertErrorResponse(responseBody, ErrorCode.ILLEGAL_OPERATION_ERROR, "Document is not in state when it can be signed");
    }

    @Test
    void testSignWhenSignatureIsInvalidThen400WithCorrectResponseIsReturned() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.WAITING, Instant.now());

        final var request = new SignDocumentRequest("invalidSignature", null);

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.post(SIGN_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);

        assertErrorResponse(responseBody, ErrorCode.DOCUMENT_INVALID_SIGNATURE_ERROR, "Invalid signature");
    }

    @Test
    void testSignWhenSignatureIsValidThen200WithCorrectResponseIsReturned() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.WAITING, documentTimestampCreated);

        final var request = new SignDocumentRequest(documentSignatureBase64, null);

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
        final var documentId = createDocumentInDatabase(signerId, documentContentId, DocumentStatus.WAITING, documentTimestampCreated);

        final var request = new SignDocumentRequest(documentSignatureBase64, null);

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
    void testSignWhenSignatureIsValidThenSignatureValidationPasses() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.WAITING, documentTimestampCreated);

        final var request = new SignDocumentRequest(documentSignatureBase64, null);

        // when
        mockMvc.perform(MockMvcRequestBuilders.post(SIGN_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        validateSignatureLevelB(documentContentId);
    }

    @Test
    void testSignWhenSignatureLevelTIsRequestedThenSignatureIsValidWithTimestamp() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.WAITING, documentTimestampCreated);

        final var request = new SignDocumentRequest(documentSignatureBase64, DocumentSignatureLevel.PADES_B_T);

        // when
        mockMvc.perform(MockMvcRequestBuilders.post(SIGN_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        validateSignatureLevelT(documentContentId);
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

        assertErrorResponse(responseBody, ErrorCode.ERROR_RESOURCE_NOT_FOUND, "Document with ID %s not found".formatted(DOCUMENT_UUID));
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

        assertErrorResponse(responseBody, ErrorCode.ILLEGAL_OPERATION_ERROR, "Document is not signed yet");
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

    @Test
    void testRejectWhenRequestWithInvalidStatusIsSentThen400WithCorrectResponseIsReturned() throws Exception {
        // given
        final var requestBody = new RejectDocumentRequest(DocumentStatus.WAITING);

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.put(REJECT_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);

        assertErrorResponse(responseBody, ErrorCode.ILLEGAL_OPERATION_ERROR, "Invalid status in the request body. Expected: REJECTED, actual: WAITING");
    }

    @Test
    void testRejectWhenDocumentIsNotFoundThen400WithCorrectResponseIsReturned() throws Exception {
        // given
        final var requestBody = new RejectDocumentRequest(DocumentStatus.REJECTED);

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.put(REJECT_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), ErrorResponse.class);

        assertErrorResponse(responseBody, ErrorCode.ERROR_RESOURCE_NOT_FOUND, "Document with ID %s not found".formatted(DOCUMENT_UUID));
    }

    @Test
    void testRejectWhenDocumentStatusIsChangedThen200WithCorrectResponseIsReturned() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.WAITING, Instant.now());

        final var requestBody = new RejectDocumentRequest(DocumentStatus.REJECTED);

        // when
        final var result = mockMvc.perform(MockMvcRequestBuilders.put(REJECT_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(result.getResponse().getContentAsString(), RejectDocumentResponse.class);

        assertRejectResponse(responseBody);
    }

    @Test
    void testRejectWhenDocumentStatusIsChangedThenCorrectValuesAreStoredIntoDatabase() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(signedDocumentContent);
        final var documentId = createDocumentInDatabase(signerId, documentContentId, DocumentStatus.SIGNED, Instant.now());

        final var requestBody = new RejectDocumentRequest(DocumentStatus.REJECTED);

        // when
        mockMvc.perform(MockMvcRequestBuilders.put(REJECT_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        assertRejectedDocument(documentId);
    }


    @Test
    void testDeleteWhenDocumentDoesNotExistThen200IsReturned() throws Exception {
        // given
        // -

        // when / then
        mockMvc.perform(MockMvcRequestBuilders.delete(DELETE_DOCUMENT_ENDPOINT, DOCUMENT_UUID))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void testDeleteWhenDocumentExistsThen200IsReturnedAndDocumentIsDeletedInDatabase() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(signedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.SIGNED, Instant.now());

        // when
        mockMvc.perform(MockMvcRequestBuilders.delete(DELETE_DOCUMENT_ENDPOINT, DOCUMENT_UUID))
                .andExpect(status().isOk())
                .andReturn();

        // then
        assertEquals(0, documentRepository.count());
        assertEquals(0, documentContentRepository.count());
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
                .externalSignerId(EXTERNAL_SIGNER_ID)
                .userId("dummyUserId")
                .csr(userCsrDerBase64)
                .certificate(userCertificateDerBase64)
                .timestampCertificateExpiration(Instant.now())
                .status(status)
                .certificateChainFromList(userCertificateChainBase64)
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
                .signer(AggregateReference.to(signerId))
                .externalId(EXTERNAL_DOCUMENT_ID)
                .documentName(DOCUMENT_NAME)
                .fileName(FILENAME)
                .fileSize(UPLOADED_FILE_SIZE)
                .hash(documentHashBase64)
                .status(status)
                .documentContent(AggregateReference.to(documentContentId))
                .build();

        final var savedDocument = documentRepository.save(document);
        return savedDocument.getId();
    }

    private void assertUploadResponse(final UploadDocumentResponse response) {
        assertDoesNotThrow(() -> UUID.fromString(response.documentId()));
        assertEquals(EXTERNAL_SIGNER_ID, response.signerId());
        assertEquals(EXTERNAL_DOCUMENT_ID, response.externalId());
        assertEquals(DOCUMENT_NAME, response.name());
        assertEquals(FILENAME, response.fileName());
        assertEquals(UPLOADED_FILE_SIZE, response.size());
        assertNotNull(response.hash());
    }

    private void assertUploadedDocument(final Document document, final String expectedDocumentId) {
        assertNotEquals(0, document.getId());
        assertEquals(Instant.now().toEpochMilli(), document.getTimestampCreated().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(expectedDocumentId, document.getDocumentId());
        assertTrue(signerRepository.existsById(document.getSigner().getId()));
        assertEquals(EXTERNAL_DOCUMENT_ID, document.getExternalId());
        assertEquals(DOCUMENT_NAME, document.getDocumentName());
        assertEquals(FILENAME, document.getFileName());
        assertEquals(UPLOADED_FILE_SIZE, document.getFileSize());
        assertNotNull(document.getHash());
        assertEquals(DocumentStatus.WAITING, document.getStatus());
        assertNull(document.getSignature());

        final var documentContent = documentContentRepository.findById(document.getDocumentContent()).orElseThrow();
        assertArrayEquals(uploadedDocumentContent, documentContent.getContent());
    }

    private void assertSignResponse(final SignDocumentResponse response) {
        assertEquals(DOCUMENT_UUID, response.documentId());

        final var expectedUri = String.format("https://signercloud.wultra.com:8080/documents/%s/download", DOCUMENT_UUID);
        assertEquals(expectedUri, response.uri());
    }

    private void assertSignedDocument(final long documentId, final long signerId, final long documentContentId) {
        final var document = documentRepository.findById(documentId).orElseThrow();
        assertEquals(documentTimestampCreated.toEpochMilli(), document.getTimestampCreated().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(Instant.now().toEpochMilli(), document.getTimestampLastUpdated().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(DOCUMENT_UUID, document.getDocumentId());
        assertEquals(signerId, document.getSigner().getId());
        assertEquals(EXTERNAL_DOCUMENT_ID, document.getExternalId());
        assertEquals(DOCUMENT_NAME, document.getDocumentName());
        assertEquals(FILENAME, document.getFileName());
        assertEquals(SIGNED_FILE_SIZE, document.getFileSize());
        assertEquals(documentContentId, document.getDocumentContent().getId());
        assertEquals(documentHashBase64, document.getHash());
        assertEquals(DocumentStatus.SIGNED, document.getStatus());
        assertEquals(documentSignatureBase64, document.getSignature());
        assertEquals(DocumentSignatureLevel.PADES_B_B, document.getSignatureLevel());

        final var documentContent = documentContentRepository.findById(document.getDocumentContent()).orElseThrow();
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

        assertEquals("bytes", response.getHeader("Accept-Ranges"));
        assertNull(response.getHeader("Content-Range"));
    }

    private static String findSeparator(final String text) {
        final var pattern = Pattern.compile("multipart/byteranges; boundary=(.+)$");
        final var matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return fail("Separator for boundary not found in header");
    }

    private String buildExpectedMultiRangesBody(final String separator) {
        final var template = """
            \r\n\
            --${boundary}\r\n\
            Content-Type: ${contentType}\r\n\
            Content-Range: bytes 0-99/${totalLength}\r\n\
            \r\n\
            ${range1}\r\n\
            --${boundary}\r\n\
            Content-Type: ${contentType}\r\n\
            Content-Range: bytes 200-299/${totalLength}\r\n\
            \r\n\
            ${range2}\r\n\
            --${boundary}--""";

        final var values = Map.of(
                "boundary", separator,
                "contentType", MediaType.APPLICATION_PDF,
                "totalLength", signedDocumentContent.length,
                "range1", new String(Arrays.copyOfRange(signedDocumentContent, 0, 100), StandardCharsets.ISO_8859_1),
                "range2", new String(Arrays.copyOfRange(signedDocumentContent, 200, 300), StandardCharsets.ISO_8859_1)
        );

        return StringSubstitutor.replace(template, values);
    }

    private void assertRejectResponse(final RejectDocumentResponse response) {
        assertEquals(DOCUMENT_UUID, response.documentId());
        assertEquals(DOCUMENT_NAME, response.name());
        assertEquals(FILENAME, response.filename());
        assertEquals(UPLOADED_FILE_SIZE, response.size());
        assertEquals(documentHashBase64, response.hash());
    }

    private void assertRejectedDocument(final long documentId) {
        final var document = documentRepository.findById(documentId).orElseThrow();

        assertEquals(DocumentStatus.REJECTED, document.getStatus());
        assertEquals(Instant.now().toEpochMilli(), document.getTimestampLastUpdated().toEpochMilli(), MILLISECONDS_DELTA);
    }

    private void validateSignatureLevelB(final long documentContentId) {
        final var validator = getValidator(documentContentId);

        final var signature = validateSignature(validator);
        assertTrue(signature.isBLevelTechnicallyValid());

        final var chain = validator.getSignatureById(signature.getId()).getCertificates();
        validateCertificateChain(chain);
    }

    private void validateSignatureLevelT(final long documentContentId) {
        final var validator = getValidator(documentContentId);

        final var signature = validateSignature(validator);
        assertTrue(signature.isTLevelTechnicallyValid());

        validateTimestamp(signature);

        final var chain = validator.getSignatureById(signature.getId()).getCertificates();
        validateCertificateChain(chain);
    }

    private PDFDocumentValidator getValidator(final long documentContentId) {
        final var documentContent = documentContentRepository.findById(documentContentId).orElseThrow();
        final var signedDocumentBytes = documentContent.getContent();

        final var signedDocument = new InMemoryDocument(signedDocumentBytes);

        final var validator = new PDFDocumentValidator(signedDocument);
        validator.setCertificateVerifier(new CommonCertificateVerifier());

        return validator;
    }

    private SignatureWrapper validateSignature(final PDFDocumentValidator validator) {
        final var simpleReport = validator.validateDocument().getSimpleReport();
        assertEquals(1, simpleReport.getSignaturesCount(), "There is not exactly one signature in document");

        final var signatureId = simpleReport.getFirstSignatureId();

        assertEquals(
                simpleReport.getSigningTime(signatureId).getTime(),
                Date.from(documentTimestampCreated).getTime(),
                MILLISECONDS_DELTA
        );

        final var signature = validator.validateDocument().getDiagnosticData().getSignatureById(signatureId);
        assertTrue(signature.isSignatureIntact());
        assertTrue(signature.isSigningCertificateIdentified());
        assertTrue(signature.isStructuralValidationValid());
        assertEquals(SignatureAlgorithm.ECDSA_SHA256, signature.getSignatureAlgorithm());

        return signature;
    }

    private void validateTimestamp(final SignatureWrapper signatureWrapper) {
        final var timestamps = signatureWrapper.getTLevelTimestamps();
        assertEquals(1, timestamps.size(), "There is not exactly one timestamp in document");

        final var timestamp = timestamps.get(0);
        assertTrue(timestamp.isSignatureValid());
        assertEquals(new Date().getTime(), timestamp.getProductionTime().getTime(), MILLISECONDS_DELTA);
    }

    private void validateCertificateChain(final List<CertificateToken> chain) {
        final var certificateChainBase64 = chain.stream()
                .map(certificateToken -> {
                    try {
                        return certificateToken.getCertificate().getEncoded();
                    } catch (final CertificateEncodingException e) {
                        return fail("Error when encoding certificate", e);
                    }
                })
                .map(certificateBytes -> Base64.getEncoder().encodeToString(certificateBytes))
                .collect(Collectors.toSet());

        final var expectedChain = Stream.concat(userCertificateChainBase64.stream(), Stream.of(userCertificateDerBase64))
                .collect(Collectors.toSet());
        assertEquals(expectedChain, certificateChainBase64, "Incorrect certificate chain in document");
    }

    private MockPart createVisualSignaturePart() throws JsonProcessingException {
        final var fieldParams = new DocumentVisualSignature.FieldParameters(
                null,
                1,
                150f,
                300f,
                200f,
                50f,
                DocumentVisualSignature.FieldParameters.Rotation.AUTOMATIC
        );

        final var textParams = new DocumentVisualSignature.TextParameters(
                "Test Signature",
                "#E65C8A",
                "#2BCB9A",
                15f,
                DocumentVisualSignature.TextParameters.TextWrapping.FILL_BOX_AND_LINEBREAK,
                DocumentVisualSignature.TextParameters.SignerTextPosition.LEFT,
                DocumentVisualSignature.TextParameters.SignerTextHorizontalAlignment.RIGHT,
                DocumentVisualSignature.TextParameters.SignerTextVerticalAlignment.BOTTOM,
                DocumentVisualSignature.TextParameters.Standard14Font.COURIER_BOLD_OBLIQUE,
                null
        );

        final var params = new DocumentVisualSignature(
                signatureImageBase64,
                300,
                DocumentVisualSignature.AlignmentHorizontal.RIGHT,
                DocumentVisualSignature.AlignmentVertical.BOTTOM,
                90,
                "#3A7DFF",
                DocumentVisualSignature.ImageScaling.CENTER,
                fieldParams,
                textParams
        );

        final var visualSignature = new ObjectMapper().writeValueAsBytes(params);

        return new MockPart(
                "visualSignature",
                "visualSignature.json",
                visualSignature,
                MediaType.APPLICATION_JSON
        );
    }

    private void assertVisualSignature(final DocumentVisualSignature visualSignature) {
        assertEquals(signatureImageBase64, visualSignature.image());
        assertEquals(300, visualSignature.dpi());
        assertEquals(DocumentVisualSignature.AlignmentHorizontal.RIGHT, visualSignature.alignmentHorizontal());
        assertEquals(DocumentVisualSignature.AlignmentVertical.BOTTOM, visualSignature.alignmentVertical());
        assertEquals(90, visualSignature.zoom());
        assertEquals("#3A7DFF", visualSignature.backgroundColor());
        assertEquals(DocumentVisualSignature.ImageScaling.CENTER, visualSignature.imageScaling());

        final var fieldParams = visualSignature.fieldParameters();
        assertNull(fieldParams.fieldId());
        assertEquals(1, fieldParams.page());
        assertEquals(150f, fieldParams.originX());
        assertEquals(300f, fieldParams.originY());
        assertEquals(200f, fieldParams.width());
        assertEquals(50f, fieldParams.height());
        assertEquals(DocumentVisualSignature.FieldParameters.Rotation.AUTOMATIC, fieldParams.rotation());

        final var textParams = visualSignature.textParameters();
        assertEquals("Test Signature", textParams.text());
        assertEquals("#E65C8A", textParams.textColor());
        assertEquals("#2BCB9A", textParams.backgroundColor());
        assertEquals(15f, textParams.padding());
        assertEquals(DocumentVisualSignature.TextParameters.TextWrapping.FILL_BOX_AND_LINEBREAK, textParams.textWrapping());
        assertEquals(DocumentVisualSignature.TextParameters.SignerTextPosition.LEFT, textParams.signerTextPosition());
        assertEquals(DocumentVisualSignature.TextParameters.SignerTextHorizontalAlignment.RIGHT, textParams.signerTextHorizontalAlignment());
        assertEquals(DocumentVisualSignature.TextParameters.SignerTextVerticalAlignment.BOTTOM, textParams.signerTextVerticalAlignment());
        assertEquals(DocumentVisualSignature.TextParameters.Standard14Font.COURIER_BOLD_OBLIQUE, textParams.standard14Font());
    }
}