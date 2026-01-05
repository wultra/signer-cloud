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
import com.wultra.signercloud.server.IntTestUtils;
import com.wultra.signercloud.server.signer.Signer;
import com.wultra.signercloud.server.signer.SignerRepository;
import com.wultra.signercloud.server.signer.SignerStatus;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.pades.validation.PDFDocumentValidator;
import eu.europa.esig.dss.spi.validation.CommonCertificateVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.security.cert.CertificateEncodingException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link DocumentController} when both {@link eu.europa.esig.dss.enumerations.DigestAlgorithm#SHA384}
 * and {@link eu.europa.esig.dss.enumerations.SignatureAlgorithm#ECDSA_SHA384} are set.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@SpringBootTest(properties = {
        "ejbca.rest-client.key-store-password=testPassword",
        "ejbca.rest-client.key-alias=testAlias",
        "ejbca.rest-client.key-password=testKeyPassword",
        "signer-cloud.server.document.waiting.timeout=",
        "signer-cloud.server.pades.signature-algorithm=ECDSA_SHA384"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class DocumentControllerSha384IntTest {

    private static final long MILLISECONDS_DELTA = 1_000;

    // Signer
    private static final String EXTERNAL_SIGNER_ID = "17056bde-9e5c-4bcc-9001-fa1fef3b5965";

    // Document
    private static final String DOCUMENT_UUID = "f58f0051-a86a-4943-a67b-c039f01f4dcb";

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

    private static String userCsrDerBase64;
    private static String userCertificateDerBase64;
    private static List<String> userCertificateChainBase64;

    private static Instant documentTimestampCreated;
    private static byte[] documentUnsignedContent;
    private static String documentHashBase64;
    private static String documentSignatureBase64;

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
        documentTimestampCreated = documentResources.timestampCreated();
        documentUnsignedContent = documentResources.unsignedContent();
        documentHashBase64 = Base64.getEncoder().encodeToString(documentResources.hashSha384());
        documentSignatureBase64 = Base64.getEncoder().encodeToString(documentResources.signatureSha384());
    }

    @AfterEach
    void tearDown() {
        documentRepository.deleteAll();
        signerRepository.deleteAll();
        documentContentRepository.deleteAll();
    }

    @Test
    void testSignWhenSha384IsUsedThenSignatureIsValid() throws Exception {
        // given
        final var signerId = createSignerInDatabase();
        final var documentContentId = createDocumentContentInDatabase(documentUnsignedContent);
        createDocumentInDatabase(signerId, documentContentId);

        final var request = new SignDocumentRequest(documentSignatureBase64, null);

        // when
        mockMvc.perform(MockMvcRequestBuilders.post("/documents/{documentId}/signature", DOCUMENT_UUID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        validateSignatureLevelB(documentContentId);
    }

    private long createSignerInDatabase() {
        final var signer = Signer.builder()
                .timestampCreated(Instant.now())
                .externalSignerId(EXTERNAL_SIGNER_ID)
                .customUserId("dummyUserId")
                .csr(userCsrDerBase64)
                .certificate(userCertificateDerBase64)
                .timestampCertificateExpiration(Instant.now())
                .status(SignerStatus.ACTIVE)
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

    private void createDocumentInDatabase(final long signerId, final long documentContentId) {
        final var document = Document.builder()
                .timestampCreated(documentTimestampCreated)
                .documentId(DOCUMENT_UUID)
                .signer(AggregateReference.to(signerId))
                .customDocumentId("test-external-document-id")
                .documentName("text-document-name")
                .fileName("input.pdf")
                .fileSize(7757)
                .hash(documentHashBase64)
                .status(DocumentStatus.WAITING)
                .documentContent(AggregateReference.to(documentContentId))
                .build();

        documentRepository.save(document);
    }

    private void validateSignatureLevelB(final long documentContentId) {
        final var validator = getValidator(documentContentId);

        final var signature = validateSignature(validator);
        assertTrue(signature.isBLevelTechnicallyValid());

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
        assertEquals(SignatureAlgorithm.ECDSA_SHA384, signature.getSignatureAlgorithm());

        return signature;
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

}
