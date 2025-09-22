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

import com.wultra.core.rest.client.base.RestClientException;
import com.wultra.signercloud.server.ejbca.EjbcaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jdbc.core.mapping.AggregateReference;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CertificateRevocationService}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@ExtendWith(MockitoExtension.class)
class CertificateRevocationServiceTest {

    private static final long ID = 1L;
    private static final long SIGNER_ID = 2L;
    private static final Instant TIMESTAMP_CREATED = Instant.now().minusSeconds(60);
    private static final String SERIAL_NUMBER = "";
    private static final String ISSUER_DN = "";
    private static final Instant TIMESTAMP_CERTIFICATE_EXPIRATION = Instant.now().plusSeconds(3600);

    private static final long MILLISECONDS_DELTA = 1_000;

    @Mock
    private EjbcaService ejbcaService;

    @Mock
    private IssuedCertificateRepository issuedCertificateRepository;

    @InjectMocks
    private CertificateRevocationService certificateRevocationService;

    @Captor
    private ArgumentCaptor<IssuedCertificateMetadata> issuedCertificateMetadataArgumentCaptor;

    @Test
    void testRevokeCertificateWhenRestExceptionIsThrownThenExceptionWithCorrectMessageIsReturned() throws RestClientException {
        // given
        final var issuedCertificateMetadata = buildIssuedCertificateMetadata();

        final var revokeRequest = EjbcaService.RevokeCertificateRequest.builder()
                .issuerDN(ISSUER_DN)
                .serialNumber(SERIAL_NUMBER)
                .build();

        doThrow(new RestClientException("Test")).when(ejbcaService).revokeCertificate(revokeRequest);

        // when
        final var exception = assertThrows(
                CertificateRevocationException.class,
                () -> certificateRevocationService.revokeCertificate(issuedCertificateMetadata)
        );

        // then
        assertEquals("Certificate could not be revoked because of EJBCA client error: Test", exception.getMessage());
    }

    @Test
    void testRevokeCertificateWhenRevocationIsSuccessfulThenIssuedCertificateIsUpdated() {
        // given
        final var issuedCertificateMetadata = buildIssuedCertificateMetadata();

        // when
        certificateRevocationService.revokeCertificate(issuedCertificateMetadata);

        // then
        verify(issuedCertificateRepository).save(issuedCertificateMetadataArgumentCaptor.capture());

        final var issuedCertificate = issuedCertificateMetadataArgumentCaptor.getValue();
        assertSavedIssuedCertificateMetadata(issuedCertificate);
    }

    private IssuedCertificateMetadata buildIssuedCertificateMetadata() {
        return IssuedCertificateMetadata.builder()
                .id(ID)
                .signer(AggregateReference.to(SIGNER_ID))
                .timestampCreated(TIMESTAMP_CREATED)
                .serialNumber(SERIAL_NUMBER)
                .issuerDn(ISSUER_DN)
                .timestampCertificateExpiration(TIMESTAMP_CERTIFICATE_EXPIRATION)
                .status(IssuedCertificateStatus.ISSUED)
                .build();
    }

    private void assertSavedIssuedCertificateMetadata(final IssuedCertificateMetadata issuedCertificateMetadata) {
        assertEquals(ID, issuedCertificateMetadata.getId());
        assertEquals(SIGNER_ID, issuedCertificateMetadata.getSigner().getId());
        assertEquals(TIMESTAMP_CREATED.toEpochMilli(), issuedCertificateMetadata.getTimestampCreated().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(Instant.now().toEpochMilli(), issuedCertificateMetadata.getTimestampLastUpdated().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(SERIAL_NUMBER, issuedCertificateMetadata.getSerialNumber());
        assertEquals(ISSUER_DN, issuedCertificateMetadata.getIssuerDn());
        assertEquals(ISSUER_DN, issuedCertificateMetadata.getIssuerDn());
        assertEquals(TIMESTAMP_CERTIFICATE_EXPIRATION, issuedCertificateMetadata.getTimestampCertificateExpiration());
        assertEquals(IssuedCertificateStatus.REVOKED, issuedCertificateMetadata.getStatus());
    }
}
