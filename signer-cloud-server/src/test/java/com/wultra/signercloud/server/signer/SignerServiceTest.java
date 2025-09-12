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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link SignerService}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@ExtendWith(MockitoExtension.class)
class SignerServiceTest {

    private static final long DUMMY_ID = 1L;
    private static final String DUMMY_EXTERNAL_SIGNER_ID = "dummyExternalSignerId";
    private static final String DUMMY_USER_ID = "dummyUserId";
    private static final String DUMMY_CSR = "dummyCsr";

    private static final String CERTIFICATE_DER_BASE64 = "MIIB+DCCAX6gAwIBAgIUQxSMGsgB+szrQpOV2AdlwcaPajwwCgYIKoZIzj0EAwMwFDESMBAGA1UEAwwJSXNzdWluZ0NBMB4XDTI1MDkxMTA4NDIxOFoXDTI3MDgxMTA5MTQ0NlowNjERMA8GA1UEAwwISm9obiBEb2UxFDASBgNVBAoMC0V4YW1wbGVDb3JwMQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABOvUMi73HbZtISS3WUk/iF/oCDEfPZPK6IBNoFbX2G4oxEHVdArN0N39koovt8Zo2ZkJQQzaSa4Ii/hbt5aetkmjgYswgYgwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAoBgNVHSUEITAfBggrBgEFBQcDAgYIKwYBBQUHAwQGCSqGSIb3LwEBBTAdBgNVHQ4EFgQU2PPiHgo5PGWHUhQNiylNjvsHIOIwDgYDVR0PAQH/BAQDAgXgMAoGCCqGSM49BAMDA2gAMGUCMQDKry6RV3+/65yDZA8o2Zib1iSYP3npwhUW+yJkNprn+vYoLpicCmNnxcRt3IEzx68CMCLZMBKfpPDQdo4jiO9OCNZstX2yUtFcHWN7Akvg+CyvFwFClfCWxr73icr2MYrxDw==";
    private static final Instant CERTIFICATE_EXPIRATION_TIMESTAMP = Instant.ofEpochMilli(1817975686000L);
    private static final String CERTIFICATE_SERIAL_NUMBER = "382960601382395725256979170171623638043940842044";
    private static final String CERTIFICATE_ISSUER_DN = "CN=IssuingCA";

    private static final int MILLISECONDS_DELTA = 1_000;

    private X509Certificate x509Certificate;

    @Mock
    private PowerAuthService powerAuthService;

    @Mock
    private EjbcaService ejbcaService;

    @Mock
    private SignerRepository signerRepository;

    @Mock
    private IssuedCertificateRepository issuedCertificateRepository;

    @InjectMocks
    private SignerService signerService;

    @Captor
    private ArgumentCaptor<IssuedCertificate> issuedCertificateCaptor;

    @BeforeEach
    void setUp() throws CertificateException {
        final var certificateBytes = Base64.getDecoder().decode(CERTIFICATE_DER_BASE64);
        x509Certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(certificateBytes));
    }

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
        final var signer = createSigner(SignerStatus.ACTIVE);

        final var certificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(DUMMY_CSR)
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .userId(DUMMY_USER_ID)
                .build();

        when(powerAuthService.isRegistrationActive(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenReturn(x509Certificate);
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.empty());
        when(signerRepository.save(any(Signer.class))).thenReturn(signer);

        final var request = new CreateUpdateSignerRequest(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_USER_ID, DUMMY_CSR);

        // when
        final var response = signerService.createUpdateSigner(request);

        // then
        assertTrue(response.isSuccess());
    }

    @Test
    void testCreateUpdateSignerWhenSignerIsCreatedThenIssuedCertificateIsSaved() throws RestClientException, CertificateException, IOException {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);

        final var certificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(DUMMY_CSR)
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .userId(DUMMY_USER_ID)
                .build();

        when(powerAuthService.isRegistrationActive(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenReturn(x509Certificate);
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.empty());
        when(signerRepository.save(any(Signer.class))).thenReturn(signer);

        final var request = new CreateUpdateSignerRequest(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_USER_ID, DUMMY_CSR);

        // when
        signerService.createUpdateSigner(request);

        // then
        assertIssuedCertificateSave();
    }

    @Test
    void testCreateUpdateSignerWhenSignerIsUpdatedThenOkResultIsReturned() throws RestClientException, CertificateException, IOException {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);

        final var certificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(DUMMY_CSR)
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .userId(DUMMY_USER_ID)
                .build();

        when(powerAuthService.isRegistrationActive(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenReturn(x509Certificate);
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));
        when(signerRepository.save(any(Signer.class))).thenReturn(signer);

        final var request = new CreateUpdateSignerRequest(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_USER_ID, DUMMY_CSR);

        // when
        final var response = signerService.createUpdateSigner(request);

        // then
        assertTrue(response.isSuccess());
    }

    @Test
    void testCreateUpdateSignerWhenSignerIsUpdatedThenIssuedCertificatedIsSaved() throws RestClientException, CertificateException, IOException {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);

        final var certificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(DUMMY_CSR)
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .userId(DUMMY_USER_ID)
                .build();

        when(powerAuthService.isRegistrationActive(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenReturn(x509Certificate);
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));
        when(signerRepository.save(any(Signer.class))).thenReturn(signer);

        final var request = new CreateUpdateSignerRequest(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_USER_ID, DUMMY_CSR);

        // when
        signerService.createUpdateSigner(request);

        // then
        assertIssuedCertificateSave();
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
    void testUpdateStatusWhenOldStatusEqualsNewStatusThenSuccessResultIsReturned() throws CertificateEncodingException {
        // given
        final var signer = createSigner(SignerStatus.BLOCKED);

        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var response = signerService.updateStatus(DUMMY_EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.BLOCKED));

        // then
        assertTrue(response.isSuccess());
    }

    @Test
    void testUpdateStatusWhenStatusTransitionIsNotValidThenFailResultIsReturned() throws CertificateEncodingException {
        // given
        final var signer = createSigner(SignerStatus.REVOKED);

        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var response = signerService.updateStatus(DUMMY_EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.ACTIVE));

        // then
        assertFalse(response.isSuccess());
    }

    @Test
    void testUpdateStatusWhenStatusTransitionIsValidThenSuccessResultIsReturned() throws CertificateEncodingException {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);

        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var response = signerService.updateStatus(DUMMY_EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.BLOCKED));

        // then
        assertTrue(response.isSuccess());
    }

    @Test
    void testUpdateStatusWhenStatusIsSetToRevokedThenEjbcaIsCalled() throws RestClientException, CertificateEncodingException {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);

        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        signerService.updateStatus(DUMMY_EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.REVOKED));

        // then
        verify(ejbcaService).revokeCertificates(DUMMY_EXTERNAL_SIGNER_ID);
    }

    @Test
    void testGetDetailWhenSignerIsNotFoundThenFailResultIsReturned() {
        // given
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.empty());

        // when
        final var result = signerService.getDetail(DUMMY_EXTERNAL_SIGNER_ID);

        // then
        assertFalse(result.isSuccess());
    }

    @Test
    void testGetDetailWhenSignerIsFoundThenSuccessResultIsReturned() throws CertificateEncodingException {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var result = signerService.getDetail(DUMMY_EXTERNAL_SIGNER_ID);

        // then
        assertTrue(result.isSuccess());
    }

    @Test
    void testGetDetailWhenSignerIsFoundThenResponseContainsCorrectValues() throws CertificateEncodingException {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);
        when(signerRepository.findByExternalSignerId(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var result = signerService.getDetail(DUMMY_EXTERNAL_SIGNER_ID);

        // then
        assertSignerDetailResponse(result.getResponse());
    }

    private Signer createSigner(final SignerStatus status) throws CertificateEncodingException {
        return Signer.builder()
                .id(DUMMY_ID)
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .userId(DUMMY_USER_ID)
                .csr(DUMMY_CSR)
                .certificate(Base64.getEncoder().encodeToString(x509Certificate.getEncoded()))
                .timestampCertificateExpiration(x509Certificate.getNotAfter().toInstant())
                .status(status)
                .timestampCreated(Instant.now())
                .build();
    }

    private void assertSignerDetailResponse(final SignerDetailResponse response) {
        assertEquals(DUMMY_EXTERNAL_SIGNER_ID, response.externalSignerId());
        assertEquals(DUMMY_USER_ID, response.userId());
        assertEquals(SignerStatus.ACTIVE, response.signerStatus());
    }

    private void assertIssuedCertificateSave() {
        verify(issuedCertificateRepository).save(issuedCertificateCaptor.capture());

        final var savedIssuedCertificate = issuedCertificateCaptor.getValue();
        assertEquals(0, savedIssuedCertificate.getId());
        assertEquals(DUMMY_ID, savedIssuedCertificate.getSignerId());
        assertEquals(
                Instant.now().toEpochMilli(),
                savedIssuedCertificate.getTimestampCreated().toEpochMilli(),
                MILLISECONDS_DELTA
        );
        assertEquals(CERTIFICATE_SERIAL_NUMBER, savedIssuedCertificate.getSerialNumber());
        assertEquals(CERTIFICATE_ISSUER_DN, savedIssuedCertificate.getIssuerDn());
        assertEquals(
                CERTIFICATE_EXPIRATION_TIMESTAMP.toEpochMilli(),
                savedIssuedCertificate.getTimestampCertificateExpiration().toEpochMilli(),
                MILLISECONDS_DELTA
        );
    }
}
