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
package com.wultra.security.signer.server.document;

import com.wultra.signercloud.server.SignerCloudServerApplication;
import com.wultra.signercloud.server.document.*;
import com.wultra.signercloud.server.signer.Signer;
import com.wultra.signercloud.server.signer.SignerRepository;
import com.wultra.signercloud.server.signer.SignerStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Class with tests for the {@link Document} and {@link DocumentRepository}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@SpringBootTest(classes = SignerCloudServerApplication.class)
@ActiveProfiles("test")
class DocumentRepositoryTest {

    private static final Instant NOW = Instant.now();

    // Signer
    private static final String SIGNER_EXTERNAL_ID = "signerExternalId1";
    private static final String USER_ID = "userId1";
    private static final String CSR = "csr1";
    private static final String CERTIFICATE = "certificate1";
    private static final Instant TIMESTAMP_CERTIFICATE_EXPIRATION = NOW.plusSeconds(3_600);

    // DocumentContent
    private static final byte[] CONTENT = "content".getBytes();

    // Document
    private static final String DOCUMENT_ID = "documentId1";
    private static final String EXTERNAL_ID = "externalId1";
    private static final String DOCUMENT_NAME = "documentName1";
    private static final String FILE_NAME = "fileName1";
    private static final int FILE_SIZE = 1;
    private static final String HASH = "hash1";
    private static final String SIGNATURE = "signature1";

    @Autowired
    private SignerRepository signerRepository;

    @Autowired
    private DocumentContentRepository documentContentRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @AfterEach
    void tearDown() {
        documentRepository.deleteAll();
        documentContentRepository.deleteAll();
        signerRepository.deleteAll();
    }

    @Test
    void testFindByIdWhenObjectExistsThenItIsLoadedWithCorrectValues() {
        // given
        final var signerId = createSigner();
        final var documentContentId = createDocumentContent();
        final var documentId = createDocument(signerId, documentContentId);

        // when
        var document = documentRepository.findById(documentId).orElseThrow();

        // then
        assertDocument(document, signerId, documentContentId);
    }

    private long createSigner() {
        var signer = Signer.builder()
                .timestampCreated(NOW)
                .signerExternalId(SIGNER_EXTERNAL_ID)
                .userId(USER_ID)
                .csr(CSR)
                .certificate(CERTIFICATE)
                .timestampCertificateExpiration(TIMESTAMP_CERTIFICATE_EXPIRATION)
                .signerStatus(SignerStatus.ACTIVE)
                .build();

        signer = signerRepository.save(signer);
        return signer.getId();
    }

    private long createDocumentContent() {
        var documentContent = DocumentContent.builder()
                .content(CONTENT)
                .build();

        documentContent = documentContentRepository.save(documentContent);
        return documentContent.getId();
    }

    private long createDocument(long signerId, long documentContentId) {
        var document = Document.builder()
                .timestampCreated(NOW)
                .documentId(DOCUMENT_ID)
                .signerId(signerId)
                .externalId(EXTERNAL_ID)
                .documentName(DOCUMENT_NAME)
                .fileName(FILE_NAME)
                .fileSize(FILE_SIZE)
                .documentContentId(documentContentId)
                .hash(HASH)
                .documentStatus(DocumentStatus.WAITING)
                .signature(SIGNATURE)
                .build();

        document = documentRepository.save(document);
        return document.getId();
    }

    private void assertDocument(Document document, long signerId, long documentContentId) {
        assertNotEquals(0, document.getId());
        assertEquals(NOW, document.getTimestampCreated());
        assertEquals(DOCUMENT_ID, document.getDocumentId());
        assertEquals(signerId, document.getSignerId());
        assertEquals(EXTERNAL_ID, document.getExternalId());
        assertEquals(DOCUMENT_NAME, document.getDocumentName());
        assertEquals(FILE_NAME, document.getFileName());
        assertEquals(FILE_SIZE, document.getFileSize());
        assertEquals(documentContentId, document.getDocumentContentId());
        assertEquals(HASH, document.getHash());
        assertEquals(DocumentStatus.WAITING, document.getDocumentStatus());
        assertEquals(SIGNATURE, document.getSignature());
    }
}
