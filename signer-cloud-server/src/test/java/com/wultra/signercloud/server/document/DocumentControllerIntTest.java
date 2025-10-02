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
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.pades.validation.PDFDocumentValidator;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import org.apache.commons.text.StringSubstitutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
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

    // keytool -certreq -alias myAlias -keystore keystore-ecdsa.p12 -storetype PKCS12 -file myrequest.csr -dname "CN=John Doe, O=ExampleCorp, C=US"
    // private key: "AJX0rDTQYUR2oXjJPkMAfECh8P2mb2S2PweW3Lo/UhIw"
    private static final String CSR_BASE64 = "MIHxMIGYAgEAMDYxETAPBgNVBAMMCEpvaG4gRG9lMRQwEgYDVQQKDAtFeGFtcGxlQ29ycDELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAT4JkHkZHQAol19w1VqkbUuBnCJhbn4LWGzCKOGa9wESUMYtjaZyRNVaB1s0smIEsWL0Gbt8HfBuaKEvJCrZBThoAAwCgYIKoZIzj0EAwIDSAAwRQIhAK8WBa/IjzKTAw+QlUqpGpN9XJ5fh2JaVhVH2Z2wsY/SAiBVXvxbo/hdOm11apJHbZv4KLSwM0/MUVg3IIPzTvXmlg==";
    private static final String CERTIFICATE_BASE64 = "MIIB+TCCAX6gAwIBAgIUdgfxsvHbkb+D10cB0tlVop1pxBwwCgYIKoZIzj0EAwMwFDESMBAGA1UEAwwJSXNzdWluZ0NBMB4XDTI1MTAwMTE3NDMyNVoXDTI3MDgxMTA5MTQ0NlowNjERMA8GA1UEAwwISm9obiBEb2UxFDASBgNVBAoMC0V4YW1wbGVDb3JwMQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABPgmQeRkdACiXX3DVWqRtS4GcImFufgtYbMIo4Zr3ARJQxi2NpnJE1VoHWzSyYgSxYvQZu3wd8G5ooS8kKtkFOGjgYswgYgwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAoBgNVHSUEITAfBggrBgEFBQcDAgYIKwYBBQUHAwQGCSqGSIb3LwEBBTAdBgNVHQ4EFgQUinG5zjCVx7zoDsQq1wQMDAp2ee0wDgYDVR0PAQH/BAQDAgXgMAoGCCqGSM49BAMDA2kAMGYCMQCIvu/aKPHIu5eaKa7/zLFZMcyBy5g+2l16ZMiNYdAlZcRXc9w1MPdRziUmG72ATPYCMQCF2ljZll2bB+MeHuifHxP6fAqXAZCTgDvjovRxWjgUWCdVYa69DKfML2Yv3QZ518g=";

    // Document
    private static final Instant DOCUMENT_TIMESTAMP_CREATED = Instant.ofEpochMilli(1759382917473L);
    private static final String DOCUMENT_UUID = "5ae8fce9-6d1e-4350-8ee6-cde0c3850b65";
    private static final String EXTERNAL_SIGNER_ID = "6fdbc9a0-7dd8-4891-adcf-ebceac188e13";
    private static final String EXTERNAL_DOCUMENT_ID = "external-document-id";
    private static final String DOCUMENT_NAME = "Document Test";
    private static final String HASH = "MYG2MBgGCSqGSIb3DQEJAzELBgkqhkiG9w0BBwEwLwYJKoZIhvcNAQkEMSIEIAMoUPx4TGkVEM9/1eP8QCpmbUUSJvVT5OcLl3hD96TdMGkGCyqGSIb3DQEJEAIvMVowWDBWMFQEIBco05OSwhsoq1BOh2Yxsrw5OarRAQOexhk3jLCQiRvBMDAwGKQWMBQxEjAQBgNVBAMMCUlzc3VpbmdDQQIUdgfxsvHbkb+D10cB0tlVop1pxBw=";

    // echo "value_of_hash" | base64 --decode > hash.bin
    // openssl pkcs12 -in keystore-ecdsa.p12 -nocerts -nodes -out mykey.pem
    // openssl dgst -sha384 -sign mykey.pem -out signature.bin hash.bin
    // base64 < signature.bin
    private static final String SIGNATURE = "MEUCIBvV6tJrAw9VyEvcBNjxl6fLSNOhTL4IQRpuAv0SqcbdAiEAhEk2Ju9QLSkllByGBvY3WXlUlucUpk14LcHVJ7DjjsU=";
    private static final String FILENAME = "input.pdf";
    private static final int UPLOADED_FILE_SIZE = 7757;
    private static final int SIGNED_FILE_SIZE = 27780;
    private static final List<String> CERTIFICATE_CHAIN_BASE64 = List.of(
            "MIIBxzCCAU2gAwIBAgIUE0be+N9+2stvvu7y3BKDiHPWBVkwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTQ0N1oXDTI3MDgxMTA5MTQ0NlowFDESMBAGA1UEAwwJSXNzdWluZ0NBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEvq7LXohpIAISl1vcnH+8zMGFAyfEnyTOqTZAP40b9PzYmMLBbHGoDxvuJwdmF/mrXxfaQ9+Ki1/QRpkoLc6Ugsywu9agdA3Zu+54GPyxmTo8MvU/txcuRt1+7UMPxTAUo2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDaAAwZQIwMlzNpdVjPFt5/sac/ZVu/56n+vNiNFOywD8Ho8SjdDNnXeBBf3zoQ2aTwPdHtgCXAjEAkNCSl2buX5U3dsxavP2gcgjrxszNQGiQJ1AcRPL1ATHnaFrHwVGNqiFX5r9QQ7ud",
            "MIIBwzCCAUqgAwIBAgIUBiKRFuSkQ2w0B+eLnFGNCVBLTfwwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTMxMFoXDTM1MDgwOTA5MTMwOVowETEPMA0GA1UEAwwGUm9vdENBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEGTbosZgh/n+FWrbU7u05huRtjxUT8PE+fuFFHBtbcKNXYSl5Jf51gMBDn2dJKbM5oRsDLpl/nwscEvRKtibnw8AsIxXZYmyzBVA9meE5FGXswp6kAb/Sc4zQYo/O8RT5o2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBQ0YPm2cX21iY8xSP6EVfPBM2RNwTAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDZwAwZAIxAKsQhZDkBpxdGzn/gxDtqbtl5VtJFl3IJzXb36hWRf26P5Vha2vLAcFipD7koHF6bwIvYJHWRuq+SAzVYue9oId39+8AGKFXvzY+xDiSb/q7+ll/CwwQwcnoRundq8TSVYE="
    );

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
                        .param(EXTERNAL_SIGNER_ID_PARAM, EXTERNAL_SIGNER_ID)
                        .param(EXTERNAL_DOCUMENT_ID_PARAM, EXTERNAL_DOCUMENT_ID)
                        .param(DOCUMENT_NAME_PARAM, DOCUMENT_NAME))
                .andExpect(status().isBadRequest())
                .andReturn();

        // when
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

        // when
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
                        .param(EXTERNAL_SIGNER_ID_PARAM, EXTERNAL_SIGNER_ID)
                        .param(EXTERNAL_DOCUMENT_ID_PARAM, EXTERNAL_DOCUMENT_ID)
                        .param(DOCUMENT_NAME_PARAM, DOCUMENT_NAME))
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

        assertErrorResponse(responseBody, ErrorCode.ERROR_RESOURCE_NOT_FOUND, "Document with ID %s not found".formatted(DOCUMENT_UUID));
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

        assertErrorResponse(responseBody, ErrorCode.ILLEGAL_OPERATION_ERROR, "Signer is not active. Signer: " + EXTERNAL_SIGNER_ID);
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

        assertErrorResponse(responseBody, ErrorCode.ILLEGAL_OPERATION_ERROR, "Document is not in state when it can be signed");
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

        assertErrorResponse(responseBody, ErrorCode.DOCUMENT_INVALID_SIGNATURE_ERROR, "Invalid signature");
    }

    @Test
    void testSignWhenSignatureIsValidThen200WithCorrectResponseIsReturned() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.WAITING, DOCUMENT_TIMESTAMP_CREATED);

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
        final var documentId = createDocumentInDatabase(signerId, documentContentId, DocumentStatus.WAITING, DOCUMENT_TIMESTAMP_CREATED);

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
    void testSignWhenSignatureIsValidThenSignatureValidationPasses() throws Exception {
        // given
        final var signerId = createSignerInDatabase(SignerStatus.ACTIVE);
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId, DocumentStatus.WAITING, DOCUMENT_TIMESTAMP_CREATED);

        final var request = new SignDocumentRequest(SIGNATURE);

        // when
        mockMvc.perform(MockMvcRequestBuilders.post(SIGN_DOCUMENT_ENDPOINT, DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        validateSignature(documentContentId);
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
                .csr(CSR_BASE64)
                .certificate(CERTIFICATE_BASE64)
                .timestampCertificateExpiration(Instant.now())
                .status(status)
                .certificateChainFromList(CERTIFICATE_CHAIN_BASE64)
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
                .hash(HASH)
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
        assertEquals(DOCUMENT_TIMESTAMP_CREATED.toEpochMilli(), document.getTimestampCreated().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(Instant.now().toEpochMilli(), document.getTimestampLastUpdated().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(DOCUMENT_UUID, document.getDocumentId());
        assertEquals(signerId, document.getSigner().getId());
        assertEquals(EXTERNAL_DOCUMENT_ID, document.getExternalId());
        assertEquals(DOCUMENT_NAME, document.getDocumentName());
        assertEquals(FILENAME, document.getFileName());
        assertEquals(SIGNED_FILE_SIZE, document.getFileSize());
        assertEquals(documentContentId, document.getDocumentContent().getId());
        assertEquals(HASH, document.getHash());
        assertEquals(DocumentStatus.SIGNED, document.getStatus());
        assertEquals(SIGNATURE, document.getSignature());

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
        assertEquals(HASH, response.hash());
    }

    private void assertRejectedDocument(final long documentId) {
        final var document = documentRepository.findById(documentId).orElseThrow();

        assertEquals(DocumentStatus.REJECTED, document.getStatus());
        assertEquals(Instant.now().toEpochMilli(), document.getTimestampLastUpdated().toEpochMilli(), MILLISECONDS_DELTA);
    }

    private void validateSignature(final long documentContentId) {
        final var documentContent = documentContentRepository.findById(documentContentId).orElseThrow();
        final var signedDocumentBytes = documentContent.getContent();

        final var signedDocument = new InMemoryDocument(signedDocumentBytes);

        final var validator = new PDFDocumentValidator(signedDocument);
        validator.setCertificateVerifier(new CommonCertificateVerifier());

        final var simpleReport = validator.validateDocument().getSimpleReport();
        assertEquals(1, simpleReport.getSignaturesCount(), "There is not exactly one signature in document");

        final var signatureId = simpleReport.getFirstSignatureId();

        assertEquals(
                simpleReport.getSigningTime(signatureId).getTime(),
                Date.from(DOCUMENT_TIMESTAMP_CREATED).getTime(),
                MILLISECONDS_DELTA
        );

        final var signature = validator.validateDocument().getDiagnosticData().getSignatureById(signatureId);
        assertTrue(signature.isBLevelTechnicallyValid());
        assertTrue(signature.isSignatureIntact());
        assertTrue(signature.isSigningCertificateIdentified());
        assertTrue(signature.isStructuralValidationValid());

        final var chain = validator.getSignatureById(signatureId).getCertificates();
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

        final var expectedChain = Stream.concat(CERTIFICATE_CHAIN_BASE64.stream(), Stream.of(CERTIFICATE_BASE64))
                .collect(Collectors.toSet());
        assertEquals(expectedChain, certificateChainBase64, "Incorrect certificate chain in document");
    }
}