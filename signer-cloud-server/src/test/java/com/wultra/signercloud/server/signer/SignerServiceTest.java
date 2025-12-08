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
import com.wultra.signercloud.server.callback.api.CallbackNotificationService;
import com.wultra.signercloud.server.callback.api.CallbackType;
import com.wultra.signercloud.server.ejbca.EjbcaService;
import com.wultra.signercloud.server.powerauth.PowerAuthService;
import com.wultra.signercloud.server.utils.CertificateUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SignerService}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@ExtendWith(MockitoExtension.class)
class SignerServiceTest {

    private static final long SIGNER_ID = 1L;
    private static final String EXTERNAL_SIGNER_ID = "2f36dcf7-3d21-4c46-93f4-f487b41e7ab7";
    private static final String USER_ID = "testUser1";
    private static final String CSR_PEM = "-----BEGIN CERTIFICATE REQUEST-----\nMIHxMIGYAgEAMDYxETAPBgNVBAMMCEpvaG4gRG9lMRQwEgYDVQQKDAtFeGFtcGxlQ29ycDELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATr1DIu9x22bSEkt1lJP4hf6AgxHz2TyuiATaBW19huKMRB1XQKzdDd/ZKKL7fGaNmZCUEM2kmuCIv4W7eWnrZJoAAwCgYIKoZIzj0EAwIDSAAwRQIgbfepkGuhZMjVQ4alNWkD8xbDP6aufd9dWPfPTvKpaRcCIQDZu9uyj+tYEyPja0/D8Xk8HvDtkkVxpfoxbA2IMINiQA==\n-----END CERTIFICATE REQUEST-----\n";
    private static final String CSR_BASE64 = "MIHxMIGYAgEAMDYxETAPBgNVBAMMCEpvaG4gRG9lMRQwEgYDVQQKDAtFeGFtcGxlQ29ycDELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATr1DIu9x22bSEkt1lJP4hf6AgxHz2TyuiATaBW19huKMRB1XQKzdDd/ZKKL7fGaNmZCUEM2kmuCIv4W7eWnrZJoAAwCgYIKoZIzj0EAwIDSAAwRQIgbfepkGuhZMjVQ4alNWkD8xbDP6aufd9dWPfPTvKpaRcCIQDZu9uyj+tYEyPja0/D8Xk8HvDtkkVxpfoxbA2IMINiQA==";

    private static final String CSR_SIGNED_DATA_BASE64 = "MIGYAgEAMDYxETAPBgNVBAMMCEpvaG4gRG9lMRQwEgYDVQQKDAtFeGFtcGxlQ29ycDELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATr1DIu9x22bSEkt1lJP4hf6AgxHz2TyuiATaBW19huKMRB1XQKzdDd/ZKKL7fGaNmZCUEM2kmuCIv4W7eWnrZJoAA=";
    private static final String CSR_SIGNATURE_BASE64 = "MEUCIG33qZBroWTI1UOGpTVpA/MWwz+mrn3fXVj3z07yqWkXAiEA2bvbso/rWBMj42tPw/F5PB7w7ZJFcaX6MWwNiDCDYkA=";

    private static final String CERTIFICATE_1_DER_BASE64 = "MIIB+DCCAX6gAwIBAgIUQxSMGsgB+szrQpOV2AdlwcaPajwwCgYIKoZIzj0EAwMwFDESMBAGA1UEAwwJSXNzdWluZ0NBMB4XDTI1MDkxMTA4NDIxOFoXDTI3MDgxMTA5MTQ0NlowNjERMA8GA1UEAwwISm9obiBEb2UxFDASBgNVBAoMC0V4YW1wbGVDb3JwMQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABOvUMi73HbZtISS3WUk/iF/oCDEfPZPK6IBNoFbX2G4oxEHVdArN0N39koovt8Zo2ZkJQQzaSa4Ii/hbt5aetkmjgYswgYgwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAoBgNVHSUEITAfBggrBgEFBQcDAgYIKwYBBQUHAwQGCSqGSIb3LwEBBTAdBgNVHQ4EFgQU2PPiHgo5PGWHUhQNiylNjvsHIOIwDgYDVR0PAQH/BAQDAgXgMAoGCCqGSM49BAMDA2gAMGUCMQDKry6RV3+/65yDZA8o2Zib1iSYP3npwhUW+yJkNprn+vYoLpicCmNnxcRt3IEzx68CMCLZMBKfpPDQdo4jiO9OCNZstX2yUtFcHWN7Akvg+CyvFwFClfCWxr73icr2MYrxDw==";
    private static final Instant CERTIFICATE_1_EXPIRATION_TIMESTAMP = Instant.ofEpochMilli(1817975686000L);
    private static final String CERTIFICATE_1_SERIAL_NUMBER = "382960601382395725256979170171623638043940842044";
    private static final List<String> CERTIFICATE_1_CHAIN_BASE64 = List.of(
            "MIIBxzCCAU2gAwIBAgIUE0be+N9+2stvvu7y3BKDiHPWBVkwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTQ0N1oXDTI3MDgxMTA5MTQ0NlowFDESMBAGA1UEAwwJSXNzdWluZ0NBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEvq7LXohpIAISl1vcnH+8zMGFAyfEnyTOqTZAP40b9PzYmMLBbHGoDxvuJwdmF/mrXxfaQ9+Ki1/QRpkoLc6Ugsywu9agdA3Zu+54GPyxmTo8MvU/txcuRt1+7UMPxTAUo2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDaAAwZQIwMlzNpdVjPFt5/sac/ZVu/56n+vNiNFOywD8Ho8SjdDNnXeBBf3zoQ2aTwPdHtgCXAjEAkNCSl2buX5U3dsxavP2gcgjrxszNQGiQJ1AcRPL1ATHnaFrHwVGNqiFX5r9QQ7ud",
            "MIIBwzCCAUqgAwIBAgIUBiKRFuSkQ2w0B+eLnFGNCVBLTfwwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTMxMFoXDTM1MDgwOTA5MTMwOVowETEPMA0GA1UEAwwGUm9vdENBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEGTbosZgh/n+FWrbU7u05huRtjxUT8PE+fuFFHBtbcKNXYSl5Jf51gMBDn2dJKbM5oRsDLpl/nwscEvRKtibnw8AsIxXZYmyzBVA9meE5FGXswp6kAb/Sc4zQYo/O8RT5o2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBQ0YPm2cX21iY8xSP6EVfPBM2RNwTAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDZwAwZAIxAKsQhZDkBpxdGzn/gxDtqbtl5VtJFl3IJzXb36hWRf26P5Vha2vLAcFipD7koHF6bwIvYJHWRuq+SAzVYue9oId39+8AGKFXvzY+xDiSb/q7+ll/CwwQwcnoRundq8TSVYE="
    );

    private static final String CERTIFICATE_2_DER_BASE64 = "MIIB+DCCAX6gAwIBAgIUC+gOMA9uasVxNZQ/ch4FIMgLhBMwCgYIKoZIzj0EAwMwFDESMBAGA1UEAwwJSXNzdWluZ0NBMB4XDTI1MDkxNTA3MTAwNFoXDTI3MDgxMTA5MTQ0NlowNjERMA8GA1UEAwwISm9obiBEb2UxFDASBgNVBAoMC0V4YW1wbGVDb3JwMQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABOvUMi73HbZtISS3WUk/iF/oCDEfPZPK6IBNoFbX2G4oxEHVdArN0N39koovt8Zo2ZkJQQzaSa4Ii/hbt5aetkmjgYswgYgwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAoBgNVHSUEITAfBggrBgEFBQcDAgYIKwYBBQUHAwQGCSqGSIb3LwEBBTAdBgNVHQ4EFgQU2PPiHgo5PGWHUhQNiylNjvsHIOIwDgYDVR0PAQH/BAQDAgXgMAoGCCqGSM49BAMDA2gAMGUCMQCzaeNiZl+rT9IUQPeUIp742ofTM6AgvXh5byd2AdbmoS+kVe5Vy4kC4vgUaozvcG0CMFQmA/I6of0+lJQNKgKzoMiTvv6JtRRIGTgYQqboZM389OP6qGXcjW/8/ffG8LcQCg==";
    private static final Instant CERTIFICATE_2_EXPIRATION_TIMESTAMP = Instant.ofEpochMilli(1817975686000L);
    private static final String CERTIFICATE_2_SERIAL_NUMBER = "67973907291189734353515319050300227960917689363";
    private static final List<String> CERTIFICATE_2_CHAIN_BASE64 = List.of(
            "MIIBwzCCAUqgAwIBAgIUBiKRFuSkQ2w0B+eLnFGNCVBLTfwwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTMxMFoXDTM1MDgwOTA5MTMwOVowETEPMA0GA1UEAwwGUm9vdENBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEGTbosZgh/n+FWrbU7u05huRtjxUT8PE+fuFFHBtbcKNXYSl5Jf51gMBDn2dJKbM5oRsDLpl/nwscEvRKtibnw8AsIxXZYmyzBVA9meE5FGXswp6kAb/Sc4zQYo/O8RT5o2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBQ0YPm2cX21iY8xSP6EVfPBM2RNwTAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDZwAwZAIxAKsQhZDkBpxdGzn/gxDtqbtl5VtJFl3IJzXb36hWRf26P5Vha2vLAcFipD7koHF6bwIvYJHWRuq+SAzVYue9oId39+8AGKFXvzY+xDiSb/q7+ll/CwwQwcnoRundq8TSVYE=",
            "MIIBxzCCAU2gAwIBAgIUE0be+N9+2stvvu7y3BKDiHPWBVkwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTQ0N1oXDTI3MDgxMTA5MTQ0NlowFDESMBAGA1UEAwwJSXNzdWluZ0NBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEvq7LXohpIAISl1vcnH+8zMGFAyfEnyTOqTZAP40b9PzYmMLBbHGoDxvuJwdmF/mrXxfaQ9+Ki1/QRpkoLc6Ugsywu9agdA3Zu+54GPyxmTo8MvU/txcuRt1+7UMPxTAUo2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDaAAwZQIwMlzNpdVjPFt5/sac/ZVu/56n+vNiNFOywD8Ho8SjdDNnXeBBf3zoQ2aTwPdHtgCXAjEAkNCSl2buX5U3dsxavP2gcgjrxszNQGiQJ1AcRPL1ATHnaFrHwVGNqiFX5r9QQ7ud"
    );

    private static final long ISSUED_CERTIFICATE_METADATA_ID = 10L;
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
    private IssuedCertificateMetadataRepository issuedCertificateMetadataRepository;

    @Mock
    private CertificateRevocationService certificateRevocationService;

    @Mock
    private CallbackNotificationService callbackNotificationService;

    @InjectMocks
    private SignerService signerService;

    @Captor
    private ArgumentCaptor<Signer> signerCaptor;

    @Captor
    private ArgumentCaptor<IssuedCertificateMetadata> issuedCertificateCaptor;

    @BeforeEach
    void setUp() throws CertificateException {
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
    void testCreateUpdateSignerWhenPowerAuthReturnErrorThenExceptionIsThrown() throws PowerAuthClientException {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_PEM);

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenThrow(new PowerAuthClientException("PowerAuth client test exception"));

        // when
        final var exception = assertThrows(
                CsrVerificationException.class,
                () -> signerService.createUpdateSigner(request)
        );

        // then
        assertEquals("Signature could not be verified due to PowerAuth error: PowerAuth client test exception", exception.getMessage());
    }

    @Test
    void testCreateUpdateSignerWhenCsrIsMalformedThenExceptionIsThrown() {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, "malformedCsr");

        // when
        final var exception = assertThrows(
                CsrProcessingException.class,
                () -> signerService.createUpdateSigner(request)
        );

        // then
        assertEquals("Error when processing CSR: long form definite-length more than 31 bits", exception.getMessage());
    }

    @Test
    void testCreateUpdateSignerWhenSignatureIsNotValidThenExceptionIsThrown() throws PowerAuthClientException {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_PEM);

        when(powerAuthService.isSignatureValid(powerAuthRequest)).thenReturn(false);

        // when
        final var exception = assertThrows(
                CsrInvalidSignatureException.class,
                () -> signerService.createUpdateSigner(request)
        );

        // then
        assertEquals("Signature is not valid.", exception.getMessage());
    }

    @Test
    void testCreateUpdateSignerWhenCertificateAuthorityClientReturnsErrorThenExceptionIsThrown() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_PEM);
        final var ejbcaException = new RestClientException("Test", HttpStatus.BAD_REQUEST, "Test response", null);

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(true);
        when(ejbcaService.enrollCertificate(ejbcaCertificateRequest)).thenThrow(ejbcaException);

        // when
        final var exception = assertThrows(
                CertificateAuthorityException.class,
                () -> signerService.createUpdateSigner(request)
        );

        // then
        assertEquals("Error from EJBCA server when enrolling certificate: Test response", exception.getMessage());
    }

    @Test
    void testCreateUpdateSignerWhenCertificateProcessingThrowsExceptionThenExceptionIsThrown() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_PEM);

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(true);
        when(ejbcaService.enrollCertificate(ejbcaCertificateRequest))
                .thenThrow(new CertificateException("Certificate test exception"));

        // when
        final var exception = assertThrows(
                CertificateProcessingException.class,
                () -> signerService.createUpdateSigner(request)
        );

        // then
        assertEquals("Error when processing enrolled certificate: Certificate test exception", exception.getMessage());
    }

    @Test
    void testCreateUpdateSignerWhenReadingCertificateThrowsExceptionThenExceptionIsThrown() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_PEM);

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(true);
        when(ejbcaService.enrollCertificate(ejbcaCertificateRequest))
                .thenThrow(new IOException("Certificate IO test exception"));

        // when
        final var exception = assertThrows(
                CertificateProcessingException.class,
                () -> signerService.createUpdateSigner(request)
        );

        // then
        assertEquals("Error when processing enrolled certificate: Certificate IO test exception", exception.getMessage());
    }

    @Test
    void testCreateUpdateSignerWhenEncodingCertificateThrowsExceptionThenExceptionIsThrown() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_PEM);

        final var certificateResponse = EjbcaService.CertificateResponse.builder()
                .certificate(x509CertificateMock)
                .chain(CERTIFICATE_1_CHAIN_BASE64)
                .build();

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(true);
        when(ejbcaService.enrollCertificate(new EjbcaService.CertificateRequest(USER_ID, EXTERNAL_SIGNER_ID, CSR_BASE64)))
                .thenReturn(certificateResponse);
        when(x509CertificateMock.getEncoded()).thenThrow(new CertificateEncodingException("Certificate encoding test exception"));

        // when
        final var exception = assertThrows(
                CertificateProcessingException.class,
                () -> signerService.createUpdateSigner(request)
        );

        // then
        assertEquals("Error when processing certificate: Certificate encoding test exception", exception.getMessage());
    }

    @Test
    void testCreateUpdateSignerWhenSignerIsCreatedThenSignerIsSaved() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_PEM);

        final var certificateResponse = EjbcaService.CertificateResponse.builder()
                .certificate(x509Certificate1)
                .chain(CERTIFICATE_1_CHAIN_BASE64)
                .build();

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenReturn(true);
        when(ejbcaService.enrollCertificate(new EjbcaService.CertificateRequest(USER_ID, EXTERNAL_SIGNER_ID, CSR_BASE64)))
                .thenReturn(certificateResponse);
        when(signerRepository.save(any(Signer.class))).thenReturn(signer);

        // when
        signerService.createUpdateSigner(request);

        // then
        assertCreateSignerSave();
    }

    @Test
    void testCreateUpdateSignerWhenSignerIsUpdatedThenNoExceptionIsThrown() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);

        final var certificateResponse = EjbcaService.CertificateResponse.builder()
                .certificate(x509Certificate1)
                .chain(CERTIFICATE_1_CHAIN_BASE64)
                .build();

        when(powerAuthService.isSignatureValid(powerAuthRequest)).thenReturn(true);
        when(ejbcaService.enrollCertificate(ejbcaCertificateRequest)).thenReturn(certificateResponse);
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.empty());
        when(signerRepository.save(any(Signer.class))).thenReturn(signer);

        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        // when
        signerService.createUpdateSigner(request);

        // then
        assertIssuedCertificateSave(CERTIFICATE_1_SERIAL_NUMBER, CERTIFICATE_1_EXPIRATION_TIMESTAMP);
    }

    @Test
    void testCreateUpdateSignerWhenSignerIsUpdatedThenSignerIsSaved() throws PowerAuthClientException, RestClientException, CertificateException, IOException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);

        final var certificateResponse = EjbcaService.CertificateResponse.builder()
                .certificate(x509Certificate2)
                .chain(CERTIFICATE_2_CHAIN_BASE64)
                .build();

        when(powerAuthService.isSignatureValid(powerAuthRequest)).thenReturn(true);
        when(ejbcaService.enrollCertificate(ejbcaCertificateRequest)).thenReturn(certificateResponse);
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

        final var certificateResponse = EjbcaService.CertificateResponse.builder()
                .certificate(x509Certificate2)
                .chain(CERTIFICATE_2_CHAIN_BASE64)
                .build();

        when(powerAuthService.isSignatureValid(powerAuthRequest)).thenReturn(true);
        when(ejbcaService.enrollCertificate(ejbcaCertificateRequest)).thenReturn(certificateResponse);
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));
        when(signerRepository.save(any(Signer.class))).thenReturn(signer);

        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        // when
        signerService.createUpdateSigner(request);

        // then
        assertIssuedCertificateSave(CERTIFICATE_2_SERIAL_NUMBER, CERTIFICATE_2_EXPIRATION_TIMESTAMP);
    }

    @Test
    void testUpdateStatusWhenSignerIsNotFoundThenExceptionIsThrown() {
        // given
        final var request = new UpdateSignerStatusRequest(SignerStatus.BLOCKED, null);

        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.empty());

        // when
        final var exception = assertThrows(
                SignerNotFoundException.class,
                () -> signerService.updateStatus(EXTERNAL_SIGNER_ID, request)
        );

        // then
        assertEquals("Signer with ID %s not found".formatted(EXTERNAL_SIGNER_ID), exception.getMessage());
    }

    @Test
    void testUpdateStatusWhenOldStatusEqualsNewStatusThenSignerIsNotUpdatedInDatabase() throws CertificateEncodingException {
        // given
        final var signer = buildSigner(SignerStatus.BLOCKED);

        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        signerService.updateStatus(EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.BLOCKED, null));

        // then
        verify(signerRepository, never()).save(any(Signer.class));
    }

    @Test
    void testUpdateStatusWhenStatusTransitionIsNotValidThenExceptionIsThrown() throws CertificateEncodingException {
        // given
        final var signer = buildSigner(SignerStatus.REVOKED);
        final var request = new UpdateSignerStatusRequest(SignerStatus.BLOCKED, null);

        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var exception = assertThrows(
                SignerStatusTransitionException.class,
                () -> signerService.updateStatus(EXTERNAL_SIGNER_ID, request)
        );

        // then
        assertEquals("Invalid status transition from REVOKED to BLOCKED", exception.getMessage());
    }

    @Test
    void testUpdateStatusWhenStatusTransitionIsValidThenSignerIsUpdatedInDatabase() throws CertificateEncodingException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);

        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        signerService.updateStatus(EXTERNAL_SIGNER_ID, new UpdateSignerStatusRequest(SignerStatus.BLOCKED, null));

        // then
        assertUpdatedSignerStatusSave();
    }

    @Test
    void testUpdateStatusWhenCertificateAuthorityReturnsErrorThenExceptionIsThrown() throws CertificateEncodingException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);
        final var issuedCertificateMetadata = buildIssuedCertificateMetadata();
        final var request = new UpdateSignerStatusRequest(SignerStatus.REVOKED, null);

        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));
        when(issuedCertificateMetadataRepository.findForRevocation(eq(SIGNER_ID), any(Instant.class))).thenReturn(List.of(issuedCertificateMetadata));
        doThrow(new CertificateProcessingException("Test", new RuntimeException()))
                .when(certificateRevocationService).revokeCertificate(issuedCertificateMetadata, RevocationReason.UNSPECIFIED);

        // when
        final var exception = assertThrows(
                CertificateProcessingException.class,
                () -> signerService.updateStatus(EXTERNAL_SIGNER_ID, request)
        );

        // then
        assertEquals("Test", exception.getMessage());
    }

    @Test
    void testUpdateStatusWhenStatusIsSetToRevokedThenCertificateAuthorityIsCalled() throws CertificateEncodingException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);
        final var issuedCertificateMetadata = buildIssuedCertificateMetadata();

        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));
        when(issuedCertificateMetadataRepository.findForRevocation(eq(SIGNER_ID), any(Instant.class))).thenReturn(List.of(issuedCertificateMetadata));

        // when
        signerService.updateStatus(
                EXTERNAL_SIGNER_ID,
                new UpdateSignerStatusRequest(SignerStatus.REVOKED, null));

        // then
        verify(certificateRevocationService).revokeCertificate(issuedCertificateMetadata, RevocationReason.UNSPECIFIED);
    }

    @Test
    void testUpdateStatusWhenRevocationReasonIsSpecifiedThenCertificateAuthorityIsCalledWithGivenReason() throws CertificateEncodingException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);
        final var issuedCertificateMetadata = buildIssuedCertificateMetadata();
        final var reason = RevocationReason.CESSATION_OF_OPERATION;

        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));
        when(issuedCertificateMetadataRepository.findForRevocation(eq(SIGNER_ID), any(Instant.class))).thenReturn(List.of(issuedCertificateMetadata));

        // when
        signerService.updateStatus(
                EXTERNAL_SIGNER_ID,
                new UpdateSignerStatusRequest(SignerStatus.REVOKED, reason));

        // then
        verify(certificateRevocationService).revokeCertificate(issuedCertificateMetadata, reason);
    }

    @Test
    void testGetDetailWhenSignerIsNotFoundThenExceptionIsThrown() {
        // given
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.empty());

        // when
        final var exception = assertThrows(
                SignerNotFoundException.class,
                () -> signerService.getDetail(EXTERNAL_SIGNER_ID)
        );

        // then
        assertEquals("Signer with ID %s not found".formatted(EXTERNAL_SIGNER_ID), exception.getMessage());
    }

    @Test
    void testGetDetailWhenSignerIsFoundThenDetailIsReturned() throws CertificateEncodingException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);
        when(signerRepository.findByExternalSignerId(EXTERNAL_SIGNER_ID)).thenReturn(Optional.of(signer));

        // when
        final var response = signerService.getDetail(EXTERNAL_SIGNER_ID);

        // then
        assertSignerDetailResponse(response);
    }

    @Test
    void testCleanupSigners_noSignerForExpiration_correctCountIsReturned() {
        // given
        when(signerRepository.findForExpiration(any(Instant.class), eq(10))).thenReturn(Collections.emptyList());

        // when
        final var count = signerService.cleanupSigners(10);

        // then
        assertEquals(0, count);
    }

    @Test
    void testCleanupSigners_noSignerForExpiration_repositoryForUpdateIsNotCalled() {
        // given
        when(signerRepository.findForExpiration(any(Instant.class), eq(10))).thenReturn(Collections.emptyList());

        // when
        signerService.cleanupSigners(10);

        // then
        verify(signerRepository, never()).markAsExpired(any(Instant.class), any());
    }

    @Test
    void testCleanupSigners_signerForExpirationFound_correctCountIsReturned() throws CertificateEncodingException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);

        when(signerRepository.findForExpiration(any(Instant.class), eq(10))).thenReturn(List.of(signer));
        when(callbackNotificationService.isCallbackEnabled(CallbackType.EXPIRED)).thenReturn(false);

        // when
        final var count = signerService.cleanupSigners(10);

        // then
        assertEquals(1, count);
    }

    @Test
    void testCleanupSigners_signerForExpirationFound_repositoryForUpdateIsCalled() throws CertificateEncodingException {
        // given
        final var signer = buildSigner(SignerStatus.ACTIVE);

        when(signerRepository.findForExpiration(any(Instant.class), eq(10))).thenReturn(List.of(signer));
        when(callbackNotificationService.isCallbackEnabled(CallbackType.EXPIRED)).thenReturn(false);

        // when
        signerService.cleanupSigners(10);

        // then
        verify(signerRepository).markAsExpired(any(Instant.class), eq(List.of(SIGNER_ID)));
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

    private void assertUpdatedSignerStatusSave() {
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
        assertEquals(CERTIFICATE_1_DER_BASE64, savedSigner.getCertificate());
        assertEquals(CERTIFICATE_1_EXPIRATION_TIMESTAMP.toEpochMilli(),
                savedSigner.getTimestampCertificateExpiration().toEpochMilli(),
                MILLISECONDS_DELTA);
        assertEquals(SignerStatus.BLOCKED, savedSigner.getStatus());
    }

    private void assertIssuedCertificateSave(final String expectedSerialNumber, final Instant expectedExpirationTimestamp) {
        verify(issuedCertificateMetadataRepository).save(issuedCertificateCaptor.capture());

        final var savedIssuedCertificate = issuedCertificateCaptor.getValue();
        assertEquals(0, savedIssuedCertificate.getId());
        assertEquals(SIGNER_ID, savedIssuedCertificate.getSigner().getId());
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

    private IssuedCertificateMetadata buildIssuedCertificateMetadata() {
        return IssuedCertificateMetadata.builder()
                .id(ISSUED_CERTIFICATE_METADATA_ID)
                .signer(AggregateReference.to(SIGNER_ID))
                .timestampCreated(Instant.now())
                .serialNumber(CERTIFICATE_1_SERIAL_NUMBER)
                .issuerDn(CERTIFICATE_ISSUER_DN)
                .timestampCertificateExpiration(CERTIFICATE_1_EXPIRATION_TIMESTAMP)
                .status(IssuedCertificateStatus.ISSUED)
                .build();
    }
}
