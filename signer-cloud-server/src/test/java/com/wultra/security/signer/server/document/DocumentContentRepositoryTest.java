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
import com.wultra.signercloud.server.document.DocumentContent;
import com.wultra.signercloud.server.document.DocumentContentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Class with tests for the {@link DocumentContent} and {@link DocumentContentRepository}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@SpringBootTest(classes = SignerCloudServerApplication.class)
@ActiveProfiles("test")
class DocumentContentRepositoryTest {

    private static final byte[] CONTENT = "content".getBytes();

    @Autowired
    private DocumentContentRepository documentContentRepository;

    @AfterEach
    void tearDown() {
        documentContentRepository.deleteAll();
    }

    @Test
    void testFindByIdWhenObjectExistsThenItIsLoadedWithCorrectValues() {
        // given
        final var documentContentId = createDocumentContent();

        // when
        var documentContent = documentContentRepository.findById(documentContentId).orElseThrow();

        // then
        assertDocumentContent(documentContent);
    }

    private long createDocumentContent() {
        var documentContent = DocumentContent.builder()
                .content(CONTENT)
                .build();

        documentContent = documentContentRepository.save(documentContent);
        return documentContent.getId();
    }

    private void assertDocumentContent(DocumentContent documentContent) {
        assertNotEquals(0, documentContent.getId());
        assertArrayEquals(CONTENT, documentContent.getContent());
    }
}
