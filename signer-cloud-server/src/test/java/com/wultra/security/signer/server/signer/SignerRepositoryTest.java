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
package com.wultra.security.signer.server.signer;

import com.wultra.signercloud.server.SignerCloudServerApplication;
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
 * Class with tests for the {@link Signer} and {@link SignerRepository}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@SpringBootTest(classes = SignerCloudServerApplication.class)
@ActiveProfiles("test")
class SignerRepositoryTest {

    private static final int MILLISECONDS_DELTA = 500;

    private static final Instant NOW = Instant.now();
    private static final String SIGNER_EXTERNAL_ID = "signerExternalId1";
    private static final String USER_ID = "userId1";
    private static final String CSR = "csr1";
    private static final String CERTIFICATE = "certificate1";
    private static final Instant TIMESTAMP_CERTIFICATE_EXPIRATION = NOW.plusSeconds(3_600);

    @Autowired
    private SignerRepository signerRepository;

    @AfterEach
    void tearDown() {
        signerRepository.deleteAll();
    }

    @Test
    void testFindByIdWhenObjectExistsThenItIsLoadedWithCorrectValues() {
        // given
        final var signerId = createSigner();

        // when
        var signer = signerRepository.findById(signerId).orElseThrow();

        // then
        assertSigner(signer);
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

    private void assertSigner(Signer signer) {
        assertNotEquals(0, signer.getId());
        assertEquals(NOW.toEpochMilli(), signer.getTimestampCreated().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(SIGNER_EXTERNAL_ID, signer.getSignerExternalId());
        assertEquals(USER_ID, signer.getUserId());
        assertEquals(CSR, signer.getCsr());
        assertEquals(CERTIFICATE, signer.getCertificate());
        assertEquals(TIMESTAMP_CERTIFICATE_EXPIRATION.toEpochMilli(), signer.getTimestampCertificateExpiration().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(SignerStatus.ACTIVE, signer.getSignerStatus());
    }
}
