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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * Integration tests for {@link IssuedCertificateMetadataRepository}
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@SpringBootTest
@ActiveProfiles("test")
@Sql
class IssuedCertificateMetadataRepositoryIntTest {

    private static final long SIGNER_ID = 1L;

    @Autowired
    private SignerRepository signerRepository;

    @Autowired
    private IssuedCertificateMetadataRepository repository;

    @AfterEach
    void tearDown() {
        repository.deleteAll();
        signerRepository.deleteAll();
    }

    @Test
    void testFindForRevocation() {
        // given
        // - see the SQL script

        // when
        final var result = repository.findForRevocation(SIGNER_ID);

        // then
        final var expectedIds = List.of(4L)
                .toArray();

        final var actualIds = result.stream()
                .map(IssuedCertificateMetadata::getId)
                .toArray();

        assertArrayEquals(expectedIds, actualIds);
    }

}
