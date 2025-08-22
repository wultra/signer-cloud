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
import com.wultra.signercloud.server.powerauth.PowerAuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SignerService}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@ExtendWith(MockitoExtension.class)
class SignerServiceTest {

    private static final String DUMMY_EXTERNAL_SIGNER_ID = "dummyExternalSignerId";
    private static final String DUMMY_USER_ID = "dummyUserId";
    private static final String DUMMY_CSR = "dummyCsr";

    private static final byte[] DUMMY_CERTIFICATE_ENCODED = "dummyCertificateEncoded".getBytes();
    private static final Date DUMMY_CERTIFICATE_EXPIRATION_DATE = new Date();

    @Mock
    private PowerAuthService powerAuthService;

    @Mock
    private EjbcaService ejbcaService;

    @Mock
    private X509Certificate certificate;

    @Mock
    private SignerRepository signerRepository;

    @InjectMocks
    private SignerService signerService;

    @Test
    void testCreateUpdateSignerWhenExternalSignerIdIsNotActiveThenFailResultIsReturned() {
        // given
        final var request = new CreateUpdateSignerRequest(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_USER_ID, DUMMY_CSR);

        // when
        final var response = signerService.createUpdateSigner(request);

        // then
        assertFalse(response.isSuccess());
    }

    @Test
    void testCreateUpdateSignerWhenExceptionIsThrownByEjbcaServiceThenFailResultIsReturned() throws RestClientException, CertificateException, IOException {
        // given
        final var certificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(DUMMY_CSR)
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .userId(DUMMY_USER_ID)
                .build();

        when(powerAuthService.isRegistrationActive(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenThrow(new RestClientException("Exception from test"));

        final var request = new CreateUpdateSignerRequest(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_USER_ID, DUMMY_CSR);

        // when
        final var response = signerService.createUpdateSigner(request);

        // then
        assertFalse(response.isSuccess());
    }

    @Test
    void testCreateUpdateSignerWhenSignerIsCreatedThenOkResultIsReturned() throws RestClientException, CertificateException, IOException {
        // given
        final var certificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(DUMMY_CSR)
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .userId(DUMMY_USER_ID)
                .build();

        when(powerAuthService.isRegistrationActive(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenReturn(certificate);
        when(certificate.getEncoded()).thenReturn(DUMMY_CERTIFICATE_ENCODED);
        when(certificate.getNotAfter()).thenReturn(DUMMY_CERTIFICATE_EXPIRATION_DATE);
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.empty());

        final var request = new CreateUpdateSignerRequest(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_USER_ID, DUMMY_CSR);

        // when
        final var response = signerService.createUpdateSigner(request);

        // then
        assertTrue(response.isSuccess());
    }

    @Test
    void testCreateUpdateSignerWhenSignerIsUpdatedThenOkResultIsReturned() throws RestClientException, CertificateException, IOException {
        // given
        final var certificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(DUMMY_CSR)
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .userId(DUMMY_USER_ID)
                .build();

        when(powerAuthService.isRegistrationActive(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenReturn(certificate);
        when(certificate.getEncoded()).thenReturn(DUMMY_CERTIFICATE_ENCODED);
        when(certificate.getNotAfter()).thenReturn(DUMMY_CERTIFICATE_EXPIRATION_DATE);
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(Signer.builder().build()));

        final var request = new CreateUpdateSignerRequest(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_USER_ID, DUMMY_CSR);

        // when
        final var response = signerService.createUpdateSigner(request);

        // then
        assertTrue(response.isSuccess());
    }

    @Test
    void testUpdateStatusWhenSignerIsNotFoundThenFailResultIsReturned() {
        // given
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.empty());

        // when
        final var response = signerService.updateStatus(DUMMY_EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.BLOCKED));

        // then
        assertFalse(response.isSuccess());
    }

    @Test
    void testUpdateStatusWhenOldStatusEqualsNewStatusThenSuccessResultIsReturned() {
        // given
        final var signer = createSigner(SignerStatus.BLOCKED);

        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var response = signerService.updateStatus(DUMMY_EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.BLOCKED));

        // then
        assertTrue(response.isSuccess());
    }

    @Test
    void testUpdateStatusWhenStatusTransitionIsNotValidThenFailResultIsReturned() {
        // given
        final var signer = createSigner(SignerStatus.REVOKED);

        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var response = signerService.updateStatus(DUMMY_EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.ACTIVE));

        // then
        assertFalse(response.isSuccess());
    }

    @Test
    void testUpdateStatusWhenStatusTransitionIsValidThenSuccessResultIsReturned() {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);

        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var response = signerService.updateStatus(DUMMY_EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.BLOCKED));

        // then
        assertTrue(response.isSuccess());
    }

    @Test
    void testUpdateStatusWhenStatusIsSetToRevokedThenEjbcaIsCalled() throws RestClientException {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);

        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        signerService.updateStatus(DUMMY_EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.REVOKED));

        // then
        verify(ejbcaService).revokeCertificates(DUMMY_EXTERNAL_SIGNER_ID);
    }

    @Test
    void testGetDetailWhenWhenSignerIsNotFoundThenFailResultIsReturned() {
        // given
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.empty());

        // when
        final var result = signerService.getDetail(DUMMY_EXTERNAL_SIGNER_ID);

        // then
        assertFalse(result.isSuccess());
    }

    @Test
    void testGetDetailWhenWhenSignerIsFoundThenSuccessResultIsReturned() {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var result = signerService.getDetail(DUMMY_EXTERNAL_SIGNER_ID);

        // then
        assertTrue(result.isSuccess());
    }

    @Test
    void testGetDetailWhenWhenSignerIsFoundThenResponseContainsCorrectValues() {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var result = signerService.getDetail(DUMMY_EXTERNAL_SIGNER_ID);

        // then
        assertSignerDetailResponse(result.getResponse());
    }

    private Signer createSigner(final SignerStatus status) {
        return Signer.builder()
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .userId(DUMMY_USER_ID)
                .csr(DUMMY_CSR)
                .certificate(Base64.getEncoder().encodeToString(DUMMY_CERTIFICATE_ENCODED))
                .timestampCertificateExpiration(DUMMY_CERTIFICATE_EXPIRATION_DATE.toInstant())
                .status(status)
                .timestampCreated(Instant.now())
                .build();
    }

    private void assertSignerDetailResponse(final SignerDetailResponse response) {
        assertEquals(DUMMY_EXTERNAL_SIGNER_ID, response.externalSignerId());
        assertEquals(DUMMY_USER_ID, response.userId());
        assertEquals(SignerStatus.ACTIVE, response.signerStatus());
    }
}
