package com.wultra.security.signer.server;

import com.wultra.signercloud.server.SignerCloudServerApplication;
import com.wultra.signercloud.server.dao.Document;
import com.wultra.signercloud.server.dao.DocumentContent;
import com.wultra.signercloud.server.dao.Signer;
import com.wultra.signercloud.server.repository.DocumentContentRepository;
import com.wultra.signercloud.server.repository.DocumentRepository;
import com.wultra.signercloud.server.repository.SignerRepository;
import com.wultra.signercloud.server.status.DocumentStatus;
import com.wultra.signercloud.server.status.SignerStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Class for Spring JDBC DAO tests.
 */
@SpringBootTest(classes = SignerCloudServerApplication.class)
public class DaoTest {

    @Autowired
    private SignerRepository signerRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentContentRepository documentContentRepository;

    /**
     * Tests all DAO save operations.
     */
    @Test
    public void testSave() {
        // create a new signer
        var signer = Signer.builder()
                .timestampCreated(Instant.now())
                .signerExternalId("signer123")
                .userId("user123")
                .csr("crs123")
                .certificate("certificate123")
                .timestampCertificateExpiration(Instant.now().plusSeconds(3_600))
                .signerStatus(SignerStatus.ACTIVE)
                .build();

        signer = signerRepository.save(signer);
        assertNotEquals(0, signer.getId());

        // create a signed document
        var documentContent = DocumentContent.builder()
                .content("Some content of the document".getBytes())
                .build();
        documentContent = documentContentRepository.save(documentContent);
        assertNotEquals(0, documentContent.getId());

        var document = Document.builder()
                .timestampCreated(Instant.now())
                .documentId("document123")
                .signerId(signer.getId())
                .externalId("external123")
                .documentName("Test Document")
                .fileName("test_document.pdf")
                .fileSize(1024)
                .documentContentId(documentContent.getId())
                .hash("hash123")
                .documentStatus(DocumentStatus.SIGNED)
                .signature("signature123")
                .build();
        document = documentRepository.save(document);
        assertNotEquals(0, document.getId());
    }
}
