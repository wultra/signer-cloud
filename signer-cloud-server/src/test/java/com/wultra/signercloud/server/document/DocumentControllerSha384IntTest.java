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
import com.wultra.signercloud.server.signer.Signer;
import com.wultra.signercloud.server.signer.SignerRepository;
import com.wultra.signercloud.server.signer.SignerStatus;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for {@link DocumentController} when {@link eu.europa.esig.dss.enumerations.DigestAlgorithm#SHA384} is set.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@SpringBootTest(properties = {
        "ejbca.rest-client.key-store-password=testPassword",
        "ejbca.rest-client.key-alias=testAlias",
        "ejbca.rest-client.key-password=testKeyPassword",
        "signer-cloud.server.document.waiting.timeout=",
        "signer-cloud.server.pades.hash-algorithm=SHA384",
        "signer-cloud.server.pades.signature-algorithm=ECDSA_SHA384"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class DocumentControllerSha384IntTest {

    private static final long MILLISECONDS_DELTA = 1_000;

    // Signer
    private static final String EXTERNAL_SIGNER_ID = "17056bde-9e5c-4bcc-9001-fa1fef3b5965";
    private static final String CSR_BASE64 = "MIIBLjCBtQIBADA2MREwDwYDVQQDDAhKb2huIERvZTEUMBIGA1UECgwLRXhhbXBsZUNvcnAxCzAJBgNVBAYTAlVTMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEQ2Z9Zsg45e2YZ89B03uhjz7LSkXuuWJW+DvT03tfdD+5bmDutM7slZzgE9fz6saNuRoBTu07qe3QkJoG1iXDOYYuTDLBp813iJOwVplFsUs11m579zSmhU31GbAtM4f/oAAwCgYIKoZIzj0EAwIDaAAwZQIxALBSF8rbVBamT1y+cJ3cn2DgHnuhNojQ67ktyO6jXYtX/vHUZCSArVbYKp7QlFWsWgIwMhvLRHnifLaKBcLVlGB8S/c6LzBg0/NCEPhIiZka0Ka2tUSscGbncam0+q+FCjqQ";
    private static final String CERTIFICATE_BASE64 = "MIICFTCCAZugAwIBAgIURn+iqCgVXFi4i0EVAeTaPHOwrNgwCgYIKoZIzj0EAwMwFDESMBAGA1UEAwwJSXNzdWluZ0NBMB4XDTI1MTAxMzA5NTIwMloXDTI3MDgxMTA5MTQ0NlowNjERMA8GA1UEAwwISm9obiBEb2UxFDASBgNVBAoMC0V4YW1wbGVDb3JwMQswCQYDVQQGEwJVUzB2MBAGByqGSM49AgEGBSuBBAAiA2IABENmfWbIOOXtmGfPQdN7oY8+y0pF7rliVvg709N7X3Q/uW5g7rTO7JWc4BPX8+rGjbkaAU7tO6nt0JCaBtYlwzmGLkwywafNd4iTsFaZRbFLNdZue/c0poVN9RmwLTOH/6OBizCBiDAMBgNVHRMBAf8EAjAAMB8GA1UdIwQYMBaAFJ0dk1DJP8vLqD/Dx15EMOEpmqkOMCgGA1UdJQQhMB8GCCsGAQUFBwMCBggrBgEFBQcDBAYJKoZIhvcvAQEFMB0GA1UdDgQWBBT9ooCMF8fza6pXM3q4FQrfJ0nkhjAOBgNVHQ8BAf8EBAMCBeAwCgYIKoZIzj0EAwMDaAAwZQIwJbloV/D6lYnlLdBVakwnLPxc0zOlzFhci+e35oMYG41W3XTYMu4uDCRqddx4tXcmAjEAqw14a30UnJL8BogiHBbpZmnQyNTKqRP6q3R73u98wz3qjyKoR90rpQBU8eI/Po6z";
    private static final List<String> CERTIFICATE_CHAIN_BASE64 = List.of(
            "MIIBxzCCAU2gAwIBAgIUE0be+N9+2stvvu7y3BKDiHPWBVkwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTQ0N1oXDTI3MDgxMTA5MTQ0NlowFDESMBAGA1UEAwwJSXNzdWluZ0NBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEvq7LXohpIAISl1vcnH+8zMGFAyfEnyTOqTZAP40b9PzYmMLBbHGoDxvuJwdmF/mrXxfaQ9+Ki1/QRpkoLc6Ugsywu9agdA3Zu+54GPyxmTo8MvU/txcuRt1+7UMPxTAUo2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDaAAwZQIwMlzNpdVjPFt5/sac/ZVu/56n+vNiNFOywD8Ho8SjdDNnXeBBf3zoQ2aTwPdHtgCXAjEAkNCSl2buX5U3dsxavP2gcgjrxszNQGiQJ1AcRPL1ATHnaFrHwVGNqiFX5r9QQ7ud",
            "MIIBwzCCAUqgAwIBAgIUBiKRFuSkQ2w0B+eLnFGNCVBLTfwwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTMxMFoXDTM1MDgwOTA5MTMwOVowETEPMA0GA1UEAwwGUm9vdENBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEGTbosZgh/n+FWrbU7u05huRtjxUT8PE+fuFFHBtbcKNXYSl5Jf51gMBDn2dJKbM5oRsDLpl/nwscEvRKtibnw8AsIxXZYmyzBVA9meE5FGXswp6kAb/Sc4zQYo/O8RT5o2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBQ0YPm2cX21iY8xSP6EVfPBM2RNwTAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDZwAwZAIxAKsQhZDkBpxdGzn/gxDtqbtl5VtJFl3IJzXb36hWRf26P5Vha2vLAcFipD7koHF6bwIvYJHWRuq+SAzVYue9oId39+8AGKFXvzY+xDiSb/q7+ll/CwwQwcnoRundq8TSVYE="
    );

    // Document
    private static final String DOCUMENT_UUID = "f58f0051-a86a-4943-a67b-c039f01f4dcb";
    private static final Instant DOCUMENT_TIMESTAMP_CREATED = Instant.parse("2025-10-13T12:03:15.123426Z");
    private static final String HASH = "MYHkMBgGCSqGSIb3DQEJAzELBgkqhkiG9w0BBwEwPwYJKoZIhvcNAQkEMTIEMCanSFpGsxmrUExda1TNi9jIZLD8+lhU4kBJJcQZ7+qUhvbQPzC2YY6Wmg2zzCJ2ajCBhgYLKoZIhvcNAQkQAi8xdzB1MHMwcTALBglghkgBZQMEAgIEMBTvwU0Hq3Wyvw/AoRWwr1EZSkCEH3QMtK5WWYFnZPerMGvGrJhr5jdOnGBRkduoeDAwMBikFjAUMRIwEAYDVQQDDAlJc3N1aW5nQ0ECFEZ/oqgoFVxYuItBFQHk2jxzsKzY";
    private static final String SIGNATURE = "MGUCMDVGyGoRokFaMIjlY+mRWTz/vq7PMjFzpzK62oA+HNKZFx3F165F+/NogdHErcIjogIxAIRJ63DTULv2l7PVxLZzb3T0alqceBqTG7HUZeHXfbi7nBP6xg+Nr6QU6TKdIO/BJg==";

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

    @BeforeAll
    static void setUp() throws IOException {
        uploadedDocumentContent = new ClassPathResource("input.pdf").getContentAsByteArray();
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
        final var documentContentId = createDocumentContentInDatabase(uploadedDocumentContent);
        createDocumentInDatabase(signerId, documentContentId);

        final var request = new SignDocumentRequest(SIGNATURE, null);

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
                .userId("dummyUserId")
                .csr(CSR_BASE64)
                .certificate(CERTIFICATE_BASE64)
                .timestampCertificateExpiration(Instant.now())
                .status(SignerStatus.ACTIVE)
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

    private void createDocumentInDatabase(final long signerId, final long documentContentId) {
        final var document = Document.builder()
                .timestampCreated(DOCUMENT_TIMESTAMP_CREATED)
                .documentId(DOCUMENT_UUID)
                .signer(AggregateReference.to(signerId))
                .externalId("test-external-document-id")
                .documentName("text-document-name")
                .fileName("input.pdf")
                .fileSize(7757)
                .hash(HASH)
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
                Date.from(DOCUMENT_TIMESTAMP_CREATED).getTime(),
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

        final var expectedChain = Stream.concat(CERTIFICATE_CHAIN_BASE64.stream(), Stream.of(CERTIFICATE_BASE64))
                .collect(Collectors.toSet());
        assertEquals(expectedChain, certificateChainBase64, "Incorrect certificate chain in document");
    }

}
