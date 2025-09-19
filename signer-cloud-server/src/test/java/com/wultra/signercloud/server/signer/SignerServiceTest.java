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
import com.wultra.security.powerauth.client.model.error.PowerAuthClientException;
import com.wultra.security.powerauth.client.model.request.VerifyECDSASignatureRequest;
import com.wultra.signercloud.server.ejbca.EjbcaService;
import com.wultra.signercloud.server.powerauth.PowerAuthService;
import com.wultra.signercloud.server.restapi.Try;
import com.wultra.signercloud.server.utils.CertificateUtils;
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

    private static final long SIGNER_ID = 1L;
    private static final String EXTERNAL_SIGNER_ID = "2f36dcf7-3d21-4c46-93f4-f487b41e7ab7";
    private static final String USER_ID = "testUser1";
    private static final String CSR_BASE64 = "MIHxMIGYAgEAMDYxETAPBgNVBAMMCEpvaG4gRG9lMRQwEgYDVQQKDAtFeGFtcGxlQ29ycDELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATr1DIu9x22bSEkt1lJP4hf6AgxHz2TyuiATaBW19huKMRB1XQKzdDd/ZKKL7fGaNmZCUEM2kmuCIv4W7eWnrZJoAAwCgYIKoZIzj0EAwIDSAAwRQIgbfepkGuhZMjVQ4alNWkD8xbDP6aufd9dWPfPTvKpaRcCIQDZu9uyj+tYEyPja0/D8Xk8HvDtkkVxpfoxbA2IMINiQA==";

    private static final String CSR_SIGNED_DATA_BASE64 = "MIGYAgEAMDYxETAPBgNVBAMMCEpvaG4gRG9lMRQwEgYDVQQKDAtFeGFtcGxlQ29ycDELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATr1DIu9x22bSEkt1lJP4hf6AgxHz2TyuiATaBW19huKMRB1XQKzdDd/ZKKL7fGaNmZCUEM2kmuCIv4W7eWnrZJoAA=";
    private static final String CSR_SIGNATURE_BASE64 = "MEUCIG33qZBroWTI1UOGpTVpA/MWwz+mrn3fXVj3z07yqWkXAiEA2bvbso/rWBMj42tPw/F5PB7w7ZJFcaX6MWwNiDCDYkA=";

    private static final String CERTIFICATE_1_DER_BASE64 = "MIIB+DCCAX6gAwIBAgIUQxSMGsgB+szrQpOV2AdlwcaPajwwCgYIKoZIzj0EAwMwFDESMBAGA1UEAwwJSXNzdWluZ0NBMB4XDTI1MDkxMTA4NDIxOFoXDTI3MDgxMTA5MTQ0NlowNjERMA8GA1UEAwwISm9obiBEb2UxFDASBgNVBAoMC0V4YW1wbGVDb3JwMQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABOvUMi73HbZtISS3WUk/iF/oCDEfPZPK6IBNoFbX2G4oxEHVdArN0N39koovt8Zo2ZkJQQzaSa4Ii/hbt5aetkmjgYswgYgwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAoBgNVHSUEITAfBggrBgEFBQcDAgYIKwYBBQUHAwQGCSqGSIb3LwEBBTAdBgNVHQ4EFgQU2PPiHgo5PGWHUhQNiylNjvsHIOIwDgYDVR0PAQH/BAQDAgXgMAoGCCqGSM49BAMDA2gAMGUCMQDKry6RV3+/65yDZA8o2Zib1iSYP3npwhUW+yJkNprn+vYoLpicCmNnxcRt3IEzx68CMCLZMBKfpPDQdo4jiO9OCNZstX2yUtFcHWN7Akvg+CyvFwFClfCWxr73icr2MYrxDw==";
    private static final Instant CERTIFICATE_1_EXPIRATION_TIMESTAMP = Instant.ofEpochMilli(1817975686000L);
    private static final String CERTIFICATE_1_SERIAL_NUMBER = "382960601382395725256979170171623638043940842044";

    private static final String CERTIFICATE_2_DER_BASE64 = "MIIB+DCCAX6gAwIBAgIUC+gOMA9uasVxNZQ/ch4FIMgLhBMwCgYIKoZIzj0EAwMwFDESMBAGA1UEAwwJSXNzdWluZ0NBMB4XDTI1MDkxNTA3MTAwNFoXDTI3MDgxMTA5MTQ0NlowNjERMA8GA1UEAwwISm9obiBEb2UxFDASBgNVBAoMC0V4YW1wbGVDb3JwMQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABOvUMi73HbZtISS3WUk/iF/oCDEfPZPK6IBNoFbX2G4oxEHVdArN0N39koovt8Zo2ZkJQQzaSa4Ii/hbt5aetkmjgYswgYgwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAoBgNVHSUEITAfBggrBgEFBQcDAgYIKwYBBQUHAwQGCSqGSIb3LwEBBTAdBgNVHQ4EFgQU2PPiHgo5PGWHUhQNiylNjvsHIOIwDgYDVR0PAQH/BAQDAgXgMAoGCCqGSM49BAMDA2gAMGUCMQCzaeNiZl+rT9IUQPeUIp742ofTM6AgvXh5byd2AdbmoS+kVe5Vy4kC4vgUaozvcG0CMFQmA/I6of0+lJQNKgKzoMiTvv6JtRRIGTgYQqboZM389OP6qGXcjW/8/ffG8LcQCg==";
    private static final Instant CERTIFICATE_2_EXPIRATION_TIMESTAMP = Instant.ofEpochMilli(1817975686000L);
    private static final String CERTIFICATE_2_SERIAL_NUMBER = "67973907291189734353515319050300227960917689363";

    private static final String CERTIFICATE_ISSUER_DN = "CN=IssuingCA";

    private static final int MILLISECONDS_DELTA = 1_000;


    private X509Certificate x509Certificate1;
    private X509Certificate x509Certificate2;
    private VerifyECDSASignatureRequest powerAuthRequest;
    private EjbcaService.CertificateRequest ejbcaCertificateRequest;

    @Mock
    private X509Certificate x509CertificateMock;

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
    private ArgumentCaptor<Signer> signerCaptor;

    @Captor
    private ArgumentCaptor<IssuedCertificate> issuedCertificateCaptor;

    @BeforeEach
    void setUp() throws CertificateException, IOException {
        x509Certificate1 = CertificateUtils.base64ToX509Certificate(CERTIFICATE_1_DER_BASE64);
        x509Certificate2 = CertificateUtils.base64ToX509Certificate(CERTIFICATE_2_DER_BASE64);

        powerAuthRequest = buildPowerAuthRequest();

        ejbcaCertificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(CSR_BASE64)
                .externalSignerId(EXTERNAL_SIGNER_ID)
                .userId(USER_ID)
                .build();
    }

    @Test
    void testCreateUpdateSignerWhenPowerAuthClientThrowsExceptionThenFailResultIsReturned() throws PowerAuthClientException {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenThrow(new PowerAuthClientException("PowerAuth client test exception"));

        // when
        final var result = signerService.createUpdateSigner(request);

        // then
        assertErrorResult(
                result,
                SignatureVerificationException.class,
                "Signature could not be verified due to PowerAuth error: PowerAuth client test exception"
        );
    }

    @Test
    void testCreateUpdateSignerWhenCsrIsMalformedThenFailResultIsReturned() {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, "malformedCsr");

        // when
        final var result = signerService.createUpdateSigner(request);

        // then
        assertErrorResult(
                result,
                SignatureVerificationException.class,
                "Error when processing CSR: long form definite-length more than 31 bits"
        );
    }

    @Test
    void testCreateUpdateSignerWhenSignatureIsNotValidThenFailResultIsReturned() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        when(powerAuthService.isSignatureValid(powerAuthRequest)).thenReturn(false);

        // when
        final var result = signerService.createUpdateSigner(request);

        // then
        assertErrorResult(
                result,
                SignatureVerificationException.class,
                "Signature is not valid. External signer ID: " + EXTERNAL_SIGNER_ID
        );
    }

    @Test
    void testCreateUpdateSignerWhenEjbcaClientThrowsExceptionThenFailResultIsReturned() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(true);
        when(ejbcaService.enrollCertificate(ejbcaCertificateRequest)).thenThrow(new RestClientException("Rest client test exception"));

        // when
        final var result = signerService.createUpdateSigner(request);

        // then
        assertErrorResult(
                result,
                CertificateEnrollmentException.class,
                "Certificate could not be enrolled due to EJBCA error: Rest client test exception"
        );
    }

    @Test
    void testCreateUpdateSignerWhenCertificateProcessingThrowsExceptionThenFailResultIsReturned() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(true);
        when(ejbcaService.enrollCertificate(ejbcaCertificateRequest))
                .thenThrow(new CertificateException("Certificate test exception"));

        // when
        final var result = signerService.createUpdateSigner(request);

        // then
        assertErrorResult(
                result,
                CertificateEnrollmentException.class,
                "Certificate could not be processed: Certificate test exception"
        );
    }

    @Test
    void testCreateUpdateSignerWhenReadingCertificateThrowsExceptionThenFailResultIsReturned() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(true);
        when(ejbcaService.enrollCertificate(ejbcaCertificateRequest))
                .thenThrow(new IOException("Certificate IO test exception"));

        // when
        final var result = signerService.createUpdateSigner(request);

        // then
        assertErrorResult(
                result,
                CertificateEnrollmentException.class,
                "Certificate could not be read: Certificate IO test exception"
        );
    }

    @Test
    void testCreateUpdateSignerWhenEncodingCertificateThrowsExceptionThenFailResultIsReturned() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(true);
        when(ejbcaService.enrollCertificate(ejbcaCertificateRequest))
                .thenReturn(x509CertificateMock);
        when(x509CertificateMock.getEncoded()).thenThrow(new CertificateEncodingException("Certificate encoding test exception"));

        // when
        final var result = signerService.createUpdateSigner(request);

        // then
        assertErrorResult(
                result,
                CertificateEnrollmentException.class,
                "Certificate could not be encoded during creation/update"
        );
    }

    @Test
    void testCreateUpdateSignerWhenSignerIsCreatedThenSuccessResultIsReturned() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(true);
        when(ejbcaService.enrollCertificate(ejbcaCertificateRequest))
                .thenReturn(x509Certificate1);
        when(signerRepository.save(any(Signer.class))).thenReturn(signer);

        // when
        final var result = signerService.createUpdateSigner(request);

        // then
        assertTrue(result.isSuccess());
    }

    @Test
    void testCreateUpdateSignerWhenSignerIsCreatedThenSignerIsSaved() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(true);
        when(ejbcaService.enrollCertificate(ejbcaCertificateRequest))
                .thenReturn(x509Certificate1);
        when(signerRepository.save(any(Signer.class))).thenReturn(signer);

        // when
        signerService.createUpdateSigner(request);

        // then
        assertCreateSignerSave();
    }

    @Test
    void testCreateUpdateSignerWhenSignerIsCreatedThenIssuedCertificateIsSaved() throws RestClientException, CertificateException, IOException, PowerAuthClientException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);

        when(powerAuthService.isSignatureValid(powerAuthRequest)).thenReturn(true);
        when(ejbcaService.enrollCertificate(ejbcaCertificateRequest)).thenReturn(x509Certificate1);
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.empty());
        when(signerRepository.save(any(Signer.class))).thenReturn(signer);

        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        // when
        signerService.createUpdateSigner(request);

        // then
        assertIssuedCertificateSave(CERTIFICATE_1_SERIAL_NUMBER, CERTIFICATE_1_EXPIRATION_TIMESTAMP);
    }

    @Test
    void testCreateUpdateSignerWhenSignerIsUpdatedThenSuccessResultIsReturned() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);
        final var signer = Signer.builder().build();

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(true);
        when(ejbcaService.enrollCertificate(ejbcaCertificateRequest))
                .thenReturn(x509Certificate2);
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));
        when(signerRepository.save(any(Signer.class))).thenReturn(signer);

        // when
        final var result = signerService.createUpdateSigner(request);

        // then
        assertTrue(result.isSuccess());
    }

    @Test
    void testCreateUpdateSignerWhenSignerIsUpdatedThenSignerIsSaved() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);

        when(powerAuthService.isSignatureValid(powerAuthRequest)).thenReturn(true);
        when(ejbcaService.enrollCertificate(ejbcaCertificateRequest)).thenReturn(x509Certificate2);
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));
        when(signerRepository.save(any(Signer.class))).thenReturn(signer);

        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        // when
        signerService.createUpdateSigner(request);

        // then
        assertUpdateSignerSave();
    }

    @Test
    void testCreateUpdateSignerWhenSignerIsUpdatedThenIssuedCertificatedIsSaved() throws RestClientException, CertificateException, IOException, PowerAuthClientException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);

        when(powerAuthService.isSignatureValid(powerAuthRequest)).thenReturn(true);
        when(ejbcaService.enrollCertificate(ejbcaCertificateRequest)).thenReturn(x509Certificate2);
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));
        when(signerRepository.save(any(Signer.class))).thenReturn(signer);

        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        // when
        signerService.createUpdateSigner(request);

        // then
        assertIssuedCertificateSave(CERTIFICATE_2_SERIAL_NUMBER, CERTIFICATE_2_EXPIRATION_TIMESTAMP);
    }

    @Test
    void testUpdateStatusWhenSignerIsNotFoundThenFailResultIsReturned() {
        // given
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.empty());

        // when
        final var response = signerService.updateStatus(EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.BLOCKED));

        // then
        assertFalse(response.isSuccess());
    }

    @Test
    void testUpdateStatusWhenOldStatusEqualsNewStatusThenSuccessResultIsReturned() throws CertificateEncodingException {
        // given
        final var signer = buildSigner(SignerStatus.BLOCKED);

        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var response = signerService.updateStatus(EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.BLOCKED));

        // then
        assertTrue(response.isSuccess());
    }

    @Test
    void testUpdateStatusWhenStatusTransitionIsNotValidThenFailResultIsReturned() throws CertificateEncodingException {
        // given
        final var signer = buildSigner(SignerStatus.REVOKED);

        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var response = signerService.updateStatus(EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.ACTIVE));

        // then
        assertFalse(response.isSuccess());
    }

    @Test
    void testUpdateStatusWhenStatusTransitionIsValidThenSuccessResultIsReturned() throws CertificateEncodingException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);

        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var response = signerService.updateStatus(EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.BLOCKED));

        // then
        assertTrue(response.isSuccess());
    }

    // TODO (michalrozehnal, 16.09.2025): Fix and add tests when certificate is revoked in EJBCA
//    @Test
//    void testUpdateStatusWhenStatusIsSetToRevokedThenEjbcaIsCalled() throws RestClientException, CertificateEncodingException {
//        // given
//        final var signer = buildSigner(SignerStatus.ACTIVE);
//
//        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));
//
//        // when
//        signerService.updateStatus(EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.REVOKED));
//
//        // then
//        verify(ejbcaService).revokeCertificates(EXTERNAL_SIGNER_ID);
//    }

    @Test
    void testGetDetailWhenSignerIsNotFoundThenFailResultIsReturned() {
        // given
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.empty());

        // when
        final var result = signerService.getDetail(EXTERNAL_SIGNER_ID);

        // then
        assertFalse(result.isSuccess());
    }

    @Test
    void testGetDetailWhenSignerIsFoundThenSuccessResultIsReturned() throws CertificateEncodingException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var result = signerService.getDetail(EXTERNAL_SIGNER_ID);

        // then
        assertTrue(result.isSuccess());
    }

    @Test
    void testGetDetailWhenSignerIsFoundThenResponseContainsCorrectValues() throws CertificateEncodingException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var result = signerService.getDetail(EXTERNAL_SIGNER_ID);

        // then
        assertSignerDetailResponse(result.getResponse());
    }

    private Signer buildSigner(final SignerStatus status) throws CertificateEncodingException {
        return Signer.builder()
                .id(SIGNER_ID)
                .externalSignerId(EXTERNAL_SIGNER_ID)
                .userId(USER_ID)
                .csr(CSR_BASE64)
                .certificate(Base64.getEncoder().encodeToString(x509Certificate1.getEncoded()))
                .timestampCertificateExpiration(x509Certificate1.getNotAfter().toInstant())
                .status(status)
                .timestampCreated(Instant.now())
                .build();
    }

    private void assertSignerDetailResponse(final SignerDetailResponse response) {
        assertEquals(EXTERNAL_SIGNER_ID, response.externalSignerId());
        assertEquals(USER_ID, response.userId());
        assertEquals(SignerStatus.ACTIVE, response.signerStatus());
    }

    private static void assertErrorResult(final Try<Void> result, final Class<?> exceptionClass, final String errorMessage) {
        assertFalse(result.isSuccess());

        final var error = result.getError();
        assertEquals(exceptionClass, error.getClass());
        assertEquals(errorMessage, error.getMessage());
    }

    private VerifyECDSASignatureRequest buildPowerAuthRequest() {
        final var request = new VerifyECDSASignatureRequest();
        request.setActivationId(EXTERNAL_SIGNER_ID);
        request.setData(CSR_SIGNED_DATA_BASE64);
        request.setSignature(CSR_SIGNATURE_BASE64);
        return request;
    }

    private void assertCreateSignerSave() {
        verify(signerRepository).save(signerCaptor.capture());

        final var savedSigner = signerCaptor.getValue();
        assertEquals(0, savedSigner.getId());
        assertEquals(Instant.now().toEpochMilli(),
                savedSigner.getTimestampCreated().toEpochMilli(),
                MILLISECONDS_DELTA);
        assertNull(savedSigner.getTimestampLastUpdated());
        assertEquals(EXTERNAL_SIGNER_ID, savedSigner.getExternalSignerId());
        assertEquals(USER_ID, savedSigner.getUserId());
        assertEquals(CSR_BASE64, savedSigner.getCsr());
        assertEquals(CERTIFICATE_1_DER_BASE64, savedSigner.getCertificate());
        assertEquals(CERTIFICATE_1_EXPIRATION_TIMESTAMP.toEpochMilli(),
                savedSigner.getTimestampCertificateExpiration().toEpochMilli(),
                MILLISECONDS_DELTA);
        assertEquals(SignerStatus.ACTIVE, savedSigner.getStatus());
    }

    private void assertUpdateSignerSave() {
        verify(signerRepository).save(signerCaptor.capture());

        final var savedSigner = signerCaptor.getValue();
        assertEquals(SIGNER_ID, savedSigner.getId());
        assertEquals(Instant.now().toEpochMilli(),
                savedSigner.getTimestampCreated().toEpochMilli(),
                MILLISECONDS_DELTA);
        assertEquals(Instant.now().toEpochMilli(),
                savedSigner.getTimestampLastUpdated().toEpochMilli(),
                MILLISECONDS_DELTA);
        assertEquals(EXTERNAL_SIGNER_ID, savedSigner.getExternalSignerId());
        assertEquals(USER_ID, savedSigner.getUserId());
        assertEquals(CSR_BASE64, savedSigner.getCsr());
        assertEquals(CERTIFICATE_2_DER_BASE64, savedSigner.getCertificate());
        assertEquals(CERTIFICATE_2_EXPIRATION_TIMESTAMP.toEpochMilli(),
                savedSigner.getTimestampCertificateExpiration().toEpochMilli(),
                MILLISECONDS_DELTA);
        assertEquals(SignerStatus.ACTIVE, savedSigner.getStatus());
    }

    private void assertIssuedCertificateSave(final String expectedSerialNumber, final Instant expectedExpirationTimestamp) {
        verify(issuedCertificateRepository).save(issuedCertificateCaptor.capture());

        final var savedIssuedCertificate = issuedCertificateCaptor.getValue();
        assertEquals(0, savedIssuedCertificate.getId());
        assertEquals(SIGNER_ID, savedIssuedCertificate.getSignerId());
        assertEquals(
                Instant.now().toEpochMilli(),
                savedIssuedCertificate.getTimestampCreated().toEpochMilli(),
                MILLISECONDS_DELTA
        );
        assertEquals(expectedSerialNumber, savedIssuedCertificate.getSerialNumber());
        assertEquals(CERTIFICATE_ISSUER_DN, savedIssuedCertificate.getIssuerDn());
        assertEquals(
                expectedExpirationTimestamp.toEpochMilli(),
                savedIssuedCertificate.getTimestampCertificateExpiration().toEpochMilli(),
                MILLISECONDS_DELTA
        );
    }
}
