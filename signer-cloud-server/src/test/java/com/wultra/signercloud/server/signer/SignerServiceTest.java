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
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
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
}
