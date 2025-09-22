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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import java.util.Date;
import java.util.List;
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

    private static final String EXTERNAL_SIGNER_ID = "2f36dcf7-3d21-4c46-93f4-f487b41e7ab7";
    private static final String USER_ID = "testUser1";
    private static final String CSR_BASE64 = "MIHxMIGYAgEAMDYxETAPBgNVBAMMCEpvaG4gRG9lMRQwEgYDVQQKDAtFeGFtcGxlQ29ycDELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATr1DIu9x22bSEkt1lJP4hf6AgxHz2TyuiATaBW19huKMRB1XQKzdDd/ZKKL7fGaNmZCUEM2kmuCIv4W7eWnrZJoAAwCgYIKoZIzj0EAwIDSAAwRQIgbfepkGuhZMjVQ4alNWkD8xbDP6aufd9dWPfPTvKpaRcCIQDZu9uyj+tYEyPja0/D8Xk8HvDtkkVxpfoxbA2IMINiQA==";

    private static final String CSR_SIGNED_DATA_BASE64 = "MIGYAgEAMDYxETAPBgNVBAMMCEpvaG4gRG9lMRQwEgYDVQQKDAtFeGFtcGxlQ29ycDELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATr1DIu9x22bSEkt1lJP4hf6AgxHz2TyuiATaBW19huKMRB1XQKzdDd/ZKKL7fGaNmZCUEM2kmuCIv4W7eWnrZJoAA=";
    private static final String CSR_SIGNATURE_BASE64 = "MEUCIG33qZBroWTI1UOGpTVpA/MWwz+mrn3fXVj3z07yqWkXAiEA2bvbso/rWBMj42tPw/F5PB7w7ZJFcaX6MWwNiDCDYkA=";

    private static final String CERTIFICATE_DER_BASE64 = "MIIB+DCCAX6gAwIBAgIUQxSMGsgB+szrQpOV2AdlwcaPajwwCgYIKoZIzj0EAwMwFDESMBAGA1UEAwwJSXNzdWluZ0NBMB4XDTI1MDkxMTA4NDIxOFoXDTI3MDgxMTA5MTQ0NlowNjERMA8GA1UEAwwISm9obiBEb2UxFDASBgNVBAoMC0V4YW1wbGVDb3JwMQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABOvUMi73HbZtISS3WUk/iF/oCDEfPZPK6IBNoFbX2G4oxEHVdArN0N39koovt8Zo2ZkJQQzaSa4Ii/hbt5aetkmjgYswgYgwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAoBgNVHSUEITAfBggrBgEFBQcDAgYIKwYBBQUHAwQGCSqGSIb3LwEBBTAdBgNVHQ4EFgQU2PPiHgo5PGWHUhQNiylNjvsHIOIwDgYDVR0PAQH/BAQDAgXgMAoGCCqGSM49BAMDA2gAMGUCMQDKry6RV3+/65yDZA8o2Zib1iSYP3npwhUW+yJkNprn+vYoLpicCmNnxcRt3IEzx68CMCLZMBKfpPDQdo4jiO9OCNZstX2yUtFcHWN7Akvg+CyvFwFClfCWxr73icr2MYrxDw==";
    private static final Date CERTIFICATE_EXPIRATION_DATE = new Date();
    private static final List<String> CERTIFICATE_CHAIN_BASE64 = List.of(
            "MIIBxzCCAU2gAwIBAgIUE0be+N9+2stvvu7y3BKDiHPWBVkwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTQ0N1oXDTI3MDgxMTA5MTQ0NlowFDESMBAGA1UEAwwJSXNzdWluZ0NBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEvq7LXohpIAISl1vcnH+8zMGFAyfEnyTOqTZAP40b9PzYmMLBbHGoDxvuJwdmF/mrXxfaQ9+Ki1/QRpkoLc6Ugsywu9agdA3Zu+54GPyxmTo8MvU/txcuRt1+7UMPxTAUo2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDaAAwZQIwMlzNpdVjPFt5/sac/ZVu/56n+vNiNFOywD8Ho8SjdDNnXeBBf3zoQ2aTwPdHtgCXAjEAkNCSl2buX5U3dsxavP2gcgjrxszNQGiQJ1AcRPL1ATHnaFrHwVGNqiFX5r9QQ7ud",
            "MIIBwzCCAUqgAwIBAgIUBiKRFuSkQ2w0B+eLnFGNCVBLTfwwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTMxMFoXDTM1MDgwOTA5MTMwOVowETEPMA0GA1UEAwwGUm9vdENBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEGTbosZgh/n+FWrbU7u05huRtjxUT8PE+fuFFHBtbcKNXYSl5Jf51gMBDn2dJKbM5oRsDLpl/nwscEvRKtibnw8AsIxXZYmyzBVA9meE5FGXswp6kAb/Sc4zQYo/O8RT5o2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBQ0YPm2cX21iY8xSP6EVfPBM2RNwTAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDZwAwZAIxAKsQhZDkBpxdGzn/gxDtqbtl5VtJFl3IJzXb36hWRf26P5Vha2vLAcFipD7koHF6bwIvYJHWRuq+SAzVYue9oId39+8AGKFXvzY+xDiSb/q7+ll/CwwQwcnoRundq8TSVYE="
    );

    private X509Certificate x509Certificate;
    private VerifyECDSASignatureRequest powerAuthRequest;

    @Mock
    private X509Certificate x509CertificateMock;

    @Mock
    private PowerAuthService powerAuthService;

    @Mock
    private EjbcaService ejbcaService;

    @Mock
    private SignerRepository signerRepository;

    @InjectMocks
    private SignerService signerService;

    @BeforeEach
    void setUp() throws CertificateException {
        final var certificateBytes = Base64.getDecoder().decode(CERTIFICATE_DER_BASE64);
        x509Certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(certificateBytes));

        powerAuthRequest = buildPowerAuthRequest();
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
    void testCreateUpdateSignerWhenSignatureIsNotValidThenFailResultIsReturned() throws PowerAuthClientException {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(false);

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
        when(ejbcaService.enrollCertificate(new EjbcaService.CertificateRequest(USER_ID, EXTERNAL_SIGNER_ID, CSR_BASE64)))
                .thenThrow(new RestClientException("Rest client test exception"));

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
        when(ejbcaService.enrollCertificate(new EjbcaService.CertificateRequest(USER_ID, EXTERNAL_SIGNER_ID, CSR_BASE64)))
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
        when(ejbcaService.enrollCertificate(new EjbcaService.CertificateRequest(USER_ID, EXTERNAL_SIGNER_ID, CSR_BASE64)))
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

        final var certificateResponse = EjbcaService.CertificateResponse.builder()
                .certificate(x509CertificateMock)
                .chain(CERTIFICATE_CHAIN_BASE64)
                .build();

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(true);
        when(ejbcaService.enrollCertificate(new EjbcaService.CertificateRequest(USER_ID, EXTERNAL_SIGNER_ID, CSR_BASE64)))
                .thenReturn(certificateResponse);
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
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        final var certificateResponse = EjbcaService.CertificateResponse.builder()
                .certificate(x509Certificate)
                .chain(CERTIFICATE_CHAIN_BASE64)
                .build();

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(true);
        when(ejbcaService.enrollCertificate(new EjbcaService.CertificateRequest(USER_ID, EXTERNAL_SIGNER_ID, CSR_BASE64)))
                .thenReturn(certificateResponse);

        // when
        final var result = signerService.createUpdateSigner(request);

        // then
        assertTrue(result.isSuccess());
    }

    @Test
    void testCreateUpdateSignerWhenSignerIsCreatedThenRepositoryIsCalled() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        final var certificateResponse = EjbcaService.CertificateResponse.builder()
                .certificate(x509Certificate)
                .chain(CERTIFICATE_CHAIN_BASE64)
                .build();

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(true);
        when(ejbcaService.enrollCertificate(new EjbcaService.CertificateRequest(USER_ID, EXTERNAL_SIGNER_ID, CSR_BASE64)))
                .thenReturn(certificateResponse);

        // when
        signerService.createUpdateSigner(request);

        // then
        verify(signerRepository).save(any(Signer.class));
    }

    @Test
    void testCreateUpdateSignerWhenSignerIsUpdatedThenSuccessResultIsReturned() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);
        final var signer = Signer.builder().build();

        final var certificateResponse = EjbcaService.CertificateResponse.builder()
                .certificate(x509Certificate)
                .chain(CERTIFICATE_CHAIN_BASE64)
                .build();

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(true);
        when(ejbcaService.enrollCertificate(new EjbcaService.CertificateRequest(USER_ID, EXTERNAL_SIGNER_ID, CSR_BASE64)))
                .thenReturn(certificateResponse);
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var result = signerService.createUpdateSigner(request);

        // then
        assertTrue(result.isSuccess());
    }

    @Test
    void testCreateUpdateSignerWhenSignerIsUpdatedThenRepositoryIsCalled() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);
        final var signer = Signer.builder().build();

        final var certificateResponse = EjbcaService.CertificateResponse.builder()
                .certificate(x509Certificate)
                .chain(CERTIFICATE_CHAIN_BASE64)
                .build();

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(true);
        when(ejbcaService.enrollCertificate(new EjbcaService.CertificateRequest(USER_ID, EXTERNAL_SIGNER_ID, CSR_BASE64)))
                .thenReturn(certificateResponse);
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        signerService.createUpdateSigner(request);

        // then
        verify(signerRepository).save(any(Signer.class));
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
    void testUpdateStatusWhenOldStatusEqualsNewStatusThenSuccessResultIsReturned() {
        // given
        final var signer = createSigner(SignerStatus.BLOCKED);

        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var response = signerService.updateStatus(EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.BLOCKED));

        // then
        assertTrue(response.isSuccess());
    }

    @Test
    void testUpdateStatusWhenStatusTransitionIsNotValidThenFailResultIsReturned() {
        // given
        final var signer = createSigner(SignerStatus.REVOKED);

        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var response = signerService.updateStatus(EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.ACTIVE));

        // then
        assertFalse(response.isSuccess());
    }

    @Test
    void testUpdateStatusWhenStatusTransitionIsValidThenSuccessResultIsReturned() {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);

        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var response = signerService.updateStatus(EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.BLOCKED));

        // then
        assertTrue(response.isSuccess());
    }

    @Test
    void testUpdateStatusWhenStatusIsSetToRevokedThenEjbcaIsCalled() throws RestClientException {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);

        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        signerService.updateStatus(EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.REVOKED));

        // then
        verify(ejbcaService).revokeCertificates(EXTERNAL_SIGNER_ID);
    }

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
    void testGetDetailWhenSignerIsFoundThenSuccessResultIsReturned() {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var result = signerService.getDetail(EXTERNAL_SIGNER_ID);

        // then
        assertTrue(result.isSuccess());
    }

    @Test
    void testGetDetailWhenSignerIsFoundThenResponseContainsCorrectValues() {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var result = signerService.getDetail(EXTERNAL_SIGNER_ID);

        // then
        assertSignerDetailResponse(result.getResponse());
    }

    private Signer createSigner(final SignerStatus status) {
        return Signer.builder()
                .externalSignerId(EXTERNAL_SIGNER_ID)
                .userId(USER_ID)
                .csr(CSR_BASE64)
                .certificate(CERTIFICATE_DER_BASE64)
                .timestampCertificateExpiration(CERTIFICATE_EXPIRATION_DATE.toInstant())
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
}
