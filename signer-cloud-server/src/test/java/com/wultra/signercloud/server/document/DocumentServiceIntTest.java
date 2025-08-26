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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for {@link DocumentService}.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@SpringBootTest(properties = {
        "signer-cloud.server.document.waiting.retention-period=1d",
        "signer-cloud.server.document.rejected.retention-period=0",
        "signer-cloud.server.document.signed.retention-period="
})
@ActiveProfiles("test")
@Transactional
@Sql
class DocumentServiceIntTest {

    private static final long ID_WAITING_1 = 1;
    private static final long ID_WAITING_2 = 4;
    private static final long ID_REJECTED = 2;
    private static final long ID_SIGNED = 3;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentService tested;

    @Test
    void testCleanup() {
        assertTrue(documentRepository.existsById(ID_WAITING_1));
        assertTrue(documentRepository.existsById(ID_WAITING_2));
        assertTrue(documentRepository.existsById(ID_REJECTED));
        assertTrue(documentRepository.existsById(ID_SIGNED));

        tested.cleanupDocuments();

        assertFalse(documentRepository.existsById(ID_WAITING_1));
        assertTrue(documentRepository.existsById(ID_WAITING_2));
        assertFalse(documentRepository.existsById(ID_REJECTED));
        assertTrue(documentRepository.existsById(ID_SIGNED));
    }
}
