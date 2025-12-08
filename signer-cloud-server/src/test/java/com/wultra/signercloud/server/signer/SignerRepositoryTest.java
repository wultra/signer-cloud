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

package com.wultra.signercloud.server.signer;

import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link SignerRepository}.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql
class SignerRepositoryTest {

    @Autowired
    private SignerRepository signerRepository;

    @Test
    void testMarkAsExpired() {
        final Instant start = Instant.now();
        final var signerIds = signerRepository.findForExpiration(Instant.now(), 1)
                .stream()
                .map(Signer::getId)
                .toList();

        signerRepository.markAsExpired(Instant.now(), signerIds);
        final Instant end = Instant.now();

        assertEquals(1, signerIds.size());
        final Long id = signerIds.get(0);
        assertEquals(3, id);

        final Signer signer = signerRepository.findById(id)
                .orElseThrow(() -> new AssertionFailedError("Signer ID: %s does not exist".formatted(id)));
        assertEquals(SignerStatus.EXPIRED, signer.getStatus());
        final Instant timestampLastUpdated = signer.getTimestampLastUpdated();
        assertNotNull(timestampLastUpdated);
        assertTrue(timestampLastUpdated.isAfter(start) && timestampLastUpdated.isBefore(end));
    }
}
