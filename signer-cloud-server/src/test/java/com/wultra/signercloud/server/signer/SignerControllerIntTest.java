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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wultra.core.rest.client.base.RestClientException;
import com.wultra.security.powerauth.client.model.error.PowerAuthClientException;
import com.wultra.security.powerauth.client.model.request.VerifyECDSASignatureRequest;
import com.wultra.signercloud.server.ejbca.EjbcaService;
import com.wultra.signercloud.server.powerauth.PowerAuthService;
import com.wultra.signercloud.server.restapi.ErrorCode;
import com.wultra.signercloud.server.restapi.ErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the {@link SignerController}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@SpringBootTest(properties = {
        "ejbca.rest-client.key-store-password=testPassword",
        "ejbca.rest-client.key-alias=testAlias",
        "ejbca.rest-client.key-password=testKeyPassword"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@WithMockUser
class SignerControllerIntTest {
    private static final int MILLISECONDS_DELTA = 1_000;

    private static final String EXTERNAL_SIGNER_ID = "2f36dcf7-3d21-4c46-93f4-f487b41e7ab7";
    private static final String USER_ID = "testUser1";
    private static final String CSR_PEM = "-----BEGIN CERTIFICATE REQUEST-----\nMIHxMIGYAgEAMDYxETAPBgNVBAMMCEpvaG4gRG9lMRQwEgYDVQQKDAtFeGFtcGxlQ29ycDELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATr1DIu9x22bSEkt1lJP4hf6AgxHz2TyuiATaBW19huKMRB1XQKzdDd/ZKKL7fGaNmZCUEM2kmuCIv4W7eWnrZJoAAwCgYIKoZIzj0EAwIDSAAwRQIgbfepkGuhZMjVQ4alNWkD8xbDP6aufd9dWPfPTvKpaRcCIQDZu9uyj+tYEyPja0/D8Xk8HvDtkkVxpfoxbA2IMINiQA==\n-----END CERTIFICATE REQUEST-----\n";
    private static final String CSR_BASE64 = "MIHxMIGYAgEAMDYxETAPBgNVBAMMCEpvaG4gRG9lMRQwEgYDVQQKDAtFeGFtcGxlQ29ycDELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATr1DIu9x22bSEkt1lJP4hf6AgxHz2TyuiATaBW19huKMRB1XQKzdDd/ZKKL7fGaNmZCUEM2kmuCIv4W7eWnrZJoAAwCgYIKoZIzj0EAwIDSAAwRQIgbfepkGuhZMjVQ4alNWkD8xbDP6aufd9dWPfPTvKpaRcCIQDZu9uyj+tYEyPja0/D8Xk8HvDtkkVxpfoxbA2IMINiQA==";
    private static final Instant TIMESTAMP_CREATED = Instant.now().minusSeconds(120);

    private static final String CSR_SIGNED_DATA_BASE64 = "MIGYAgEAMDYxETAPBgNVBAMMCEpvaG4gRG9lMRQwEgYDVQQKDAtFeGFtcGxlQ29ycDELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATr1DIu9x22bSEkt1lJP4hf6AgxHz2TyuiATaBW19huKMRB1XQKzdDd/ZKKL7fGaNmZCUEM2kmuCIv4W7eWnrZJoAA=";
    private static final String CSR_SIGNATURE_BASE64 = "MEUCIG33qZBroWTI1UOGpTVpA/MWwz+mrn3fXVj3z07yqWkXAiEA2bvbso/rWBMj42tPw/F5PB7w7ZJFcaX6MWwNiDCDYkA=";

    private static final String CERTIFICATE_DER_BASE64 = "MIIB+DCCAX6gAwIBAgIUQxSMGsgB+szrQpOV2AdlwcaPajwwCgYIKoZIzj0EAwMwFDESMBAGA1UEAwwJSXNzdWluZ0NBMB4XDTI1MDkxMTA4NDIxOFoXDTI3MDgxMTA5MTQ0NlowNjERMA8GA1UEAwwISm9obiBEb2UxFDASBgNVBAoMC0V4YW1wbGVDb3JwMQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABOvUMi73HbZtISS3WUk/iF/oCDEfPZPK6IBNoFbX2G4oxEHVdArN0N39koovt8Zo2ZkJQQzaSa4Ii/hbt5aetkmjgYswgYgwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAoBgNVHSUEITAfBggrBgEFBQcDAgYIKwYBBQUHAwQGCSqGSIb3LwEBBTAdBgNVHQ4EFgQU2PPiHgo5PGWHUhQNiylNjvsHIOIwDgYDVR0PAQH/BAQDAgXgMAoGCCqGSM49BAMDA2gAMGUCMQDKry6RV3+/65yDZA8o2Zib1iSYP3npwhUW+yJkNprn+vYoLpicCmNnxcRt3IEzx68CMCLZMBKfpPDQdo4jiO9OCNZstX2yUtFcHWN7Akvg+CyvFwFClfCWxr73icr2MYrxDw==";
    private static final Instant CERTIFICATE_EXPIRATION_TIMESTAMP = Instant.ofEpochMilli(1817975686000L);
    private static final List<String> CERTIFICATE_CHAIN_BASE64 = List.of(
            "MIIBxzCCAU2gAwIBAgIUE0be+N9+2stvvu7y3BKDiHPWBVkwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTQ0N1oXDTI3MDgxMTA5MTQ0NlowFDESMBAGA1UEAwwJSXNzdWluZ0NBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEvq7LXohpIAISl1vcnH+8zMGFAyfEnyTOqTZAP40b9PzYmMLBbHGoDxvuJwdmF/mrXxfaQ9+Ki1/QRpkoLc6Ugsywu9agdA3Zu+54GPyxmTo8MvU/txcuRt1+7UMPxTAUo2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDaAAwZQIwMlzNpdVjPFt5/sac/ZVu/56n+vNiNFOywD8Ho8SjdDNnXeBBf3zoQ2aTwPdHtgCXAjEAkNCSl2buX5U3dsxavP2gcgjrxszNQGiQJ1AcRPL1ATHnaFrHwVGNqiFX5r9QQ7ud",
            "MIIBwzCCAUqgAwIBAgIUBiKRFuSkQ2w0B+eLnFGNCVBLTfwwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTMxMFoXDTM1MDgwOTA5MTMwOVowETEPMA0GA1UEAwwGUm9vdENBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEGTbosZgh/n+FWrbU7u05huRtjxUT8PE+fuFFHBtbcKNXYSl5Jf51gMBDn2dJKbM5oRsDLpl/nwscEvRKtibnw8AsIxXZYmyzBVA9meE5FGXswp6kAb/Sc4zQYo/O8RT5o2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBQ0YPm2cX21iY8xSP6EVfPBM2RNwTAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDZwAwZAIxAKsQhZDkBpxdGzn/gxDtqbtl5VtJFl3IJzXb36hWRf26P5Vha2vLAcFipD7koHF6bwIvYJHWRuq+SAzVYue9oId39+8AGKFXvzY+xDiSb/q7+ll/CwwQwcnoRundq8TSVYE="
    );
    private static final String CERTIFICATE_ISSUER_DN = "CN=IssuingCA";
    private static final String ISSUED_CERTIFICATE_1_SERIAL_NUMBER = "382960601382395725256979170171623638043940842044";
    private static final String ISSUED_CERTIFICATE_1_SERIAL_NUMBER_HEX = "43148c1ac801facceb429395d80765c1c68f6a3c";
    private static final String ISSUED_CERTIFICATE_2_SERIAL_NUMBER = "67973907291189734353515319050300227960917689363";

    private static final String CREATE_UPDATE_SIGNER_ENDPOINT = "/signers";
    private static final String SIGNER_ENDPOINT_WITH_ID = "/signers/{externalSignerId}";

    private X509Certificate x509Certificate;
    private VerifyECDSASignatureRequest powerAuthRequest;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SignerRepository signerRepository;

    @Autowired
    private IssuedCertificateMetadataRepository issuedCertificateMetadataRepository;

    @MockitoBean
    private PowerAuthService powerAuthService;

    @MockitoBean
    private EjbcaService ejbcaService;

    @BeforeEach
    void setUp() throws CertificateException {
        final var certificateBytes = Base64.getDecoder().decode(CERTIFICATE_DER_BASE64);
        x509Certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(certificateBytes));

        powerAuthRequest = buildPowerAuthRequest();
    }

    @AfterEach
    void tearDown() {
        issuedCertificateMetadataRepository.deleteAll();
        signerRepository.deleteAll();
    }

    @Test
    void testCreateUpdateWhenSignatureVerificationFailsThenErrorResponseIsReturned() throws Exception {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_PEM);

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenThrow(new PowerAuthClientException("PowerAuth test exception"));

        // when
        final var mvcResult = mockMvc.perform(post(CREATE_UPDATE_SIGNER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andReturn();

        // then
        final var errorResponse = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), ErrorResponse.class);
        assertErrorResponse(errorResponse, ErrorCode.SIGNATURE_VERIFICATION_ERROR, "Signature could not be verified due to PowerAuth error: PowerAuth test exception");
    }

    @Test
    void testCreateUpdateWhenCertificateEnrollmentFailsThenErrorResponseIsReturned() throws Exception {
        // given
        final var certificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(CSR_BASE64)
                .externalSignerId(EXTERNAL_SIGNER_ID)
                .userId(USER_ID)
                .build();

        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_PEM);
        final var ejbcaException = new RestClientException("Test", HttpStatus.BAD_REQUEST, "Test response", null);

        when(powerAuthService.isSignatureValid(powerAuthRequest)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenThrow(ejbcaException);

        // when
        final var mvcResult = mockMvc.perform(post(CREATE_UPDATE_SIGNER_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var errorResponse = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), ErrorResponse.class);
        assertErrorResponse(errorResponse, ErrorCode.EJBCA_ERROR, "Error from EJBCA server when enrolling certificate: Test response");
    }

    @Test
    void testCreateUpdateWhenOperationFailsThenNothingIsStoredIntoDatabase() throws Exception {
        // given
        final var certificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(CSR_BASE64)
                .externalSignerId(EXTERNAL_SIGNER_ID)
                .userId(USER_ID)
                .build();

        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_PEM);

        when(powerAuthService.isSignatureValid(powerAuthRequest)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenThrow(new RestClientException("EJBCA test exception"));

        // when
        mockMvc.perform(post(CREATE_UPDATE_SIGNER_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // then
        assertEquals(0, signerRepository.count());
    }

    @Test
    void testCreateUpdateWhenSignerIsCreatedThenOkResponseIsReturned() throws Exception {
        // given
        final var certificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(CSR_BASE64)
                .externalSignerId(EXTERNAL_SIGNER_ID)
                .userId(USER_ID)
                .build();

        final var certificateResponse = EjbcaService.CertificateResponse.builder()
                .certificate(x509Certificate)
                .chain(CERTIFICATE_CHAIN_BASE64)
                .build();

        when(powerAuthService.isSignatureValid(powerAuthRequest)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenReturn(certificateResponse);

        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_PEM);

        // when, then
        mockMvc.perform(post(CREATE_UPDATE_SIGNER_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void testCreateUpdateWhenSignerIsCreatedThenItIsStoredIntoDatabase() throws Exception {
        // given
        final var certificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(CSR_BASE64)
                .externalSignerId(EXTERNAL_SIGNER_ID)
                .userId(USER_ID)
                .build();

        final var certificateResponse = EjbcaService.CertificateResponse.builder()
                .certificate(x509Certificate)
                .chain(CERTIFICATE_CHAIN_BASE64)
                .build();

        when(powerAuthService.isSignatureValid(powerAuthRequest)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenReturn(certificateResponse);

        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_PEM);

        // when
        mockMvc.perform(post(CREATE_UPDATE_SIGNER_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // then
        final var signer = signerRepository.findAll().iterator().next();
        assertSigner(signer, Instant.now(), SignerStatus.ACTIVE);
        assertNull(signer.getTimestampLastUpdated());
    }

    @Test
    void testCreateUpdateWhenSignerIsUpdatedThenOkResponseIsReturned() throws Exception {
        // given
        createSigner(SignerStatus.ACTIVE);

        final var certificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(CSR_BASE64)
                .externalSignerId(EXTERNAL_SIGNER_ID)
                .userId(USER_ID)
                .build();

        final var certificateResponse = EjbcaService.CertificateResponse.builder()
                .certificate(x509Certificate)
                .chain(CERTIFICATE_CHAIN_BASE64)
                .build();

        when(powerAuthService.isSignatureValid(powerAuthRequest)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenReturn(certificateResponse);

        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_PEM);

        // when, then
        mockMvc.perform(post(CREATE_UPDATE_SIGNER_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void testCreateUpdateWhenSignerIsUpdatedThenItIsStoredIntoDatabase() throws Exception {
        // given
        createSigner(SignerStatus.ACTIVE);

        final var certificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(CSR_BASE64)
                .externalSignerId(EXTERNAL_SIGNER_ID)
                .userId(USER_ID)
                .build();

        final var certificateResponse = EjbcaService.CertificateResponse.builder()
                .certificate(x509Certificate)
                .chain(CERTIFICATE_CHAIN_BASE64)
                .build();

        when(powerAuthService.isSignatureValid(powerAuthRequest)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenReturn(certificateResponse);

        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_PEM);

        // when
        mockMvc.perform(post(CREATE_UPDATE_SIGNER_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // then
        final var signer = signerRepository.findAll().iterator().next();
        assertSigner(signer, TIMESTAMP_CREATED, SignerStatus.ACTIVE);
        assertEquals(Instant.now().toEpochMilli(), signer.getTimestampLastUpdated().toEpochMilli(), MILLISECONDS_DELTA);
    }

    @Test
    void testUpdateStatusWhenSignerIsNotFoundThenErrorResponseIsReturned() throws Exception {
        // given
        final var request = new UpdateSignerStatusRequest(SignerStatus.BLOCKED, null);

        // when
        final var mvcResult = mockMvc.perform(put(SIGNER_ENDPOINT_WITH_ID, EXTERNAL_SIGNER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var errorResponse = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), ErrorResponse.class);
        assertErrorResponse(errorResponse, ErrorCode.ERROR_RESOURCE_NOT_FOUND, "Signer with ID %s not found".formatted(EXTERNAL_SIGNER_ID));
    }

    @Test
    void testUpdateStatusWhenStatusTransitionIsNotValidThenErrorResponseIsReturned() throws Exception {
        // given
        createSigner(SignerStatus.REVOKED);
        final var request = new UpdateSignerStatusRequest(SignerStatus.ACTIVE, null);

        // when
        final var mvcResult = mockMvc.perform(put(SIGNER_ENDPOINT_WITH_ID, EXTERNAL_SIGNER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andReturn();

        // then
        final var errorResponse = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), ErrorResponse.class);
        assertErrorResponse(errorResponse, ErrorCode.SIGNER_STATUS_TRANSITION_ERROR, "Invalid status transition from REVOKED to ACTIVE");
    }

    @Test
    void testUpdateStatusWhenStatusTransitionIsValidThenSuccessIsReturned() throws Exception {
        // given
        createSigner(SignerStatus.ACTIVE);
        final var request = new UpdateSignerStatusRequest(SignerStatus.BLOCKED, null);

        // when, then
        mockMvc.perform(put(SIGNER_ENDPOINT_WITH_ID, EXTERNAL_SIGNER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
    }

    @Test
    void testUpdateStatusWhenStatusTransitionIsValidThenStatusIsUpdatedInDatabase() throws Exception {
        // given
        createSigner(SignerStatus.ACTIVE);
        final var request = new UpdateSignerStatusRequest(SignerStatus.BLOCKED, null);

        // when
        mockMvc.perform(put(SIGNER_ENDPOINT_WITH_ID, EXTERNAL_SIGNER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // then
        final var signer = signerRepository.findAll().iterator().next();
        assertSigner(signer, TIMESTAMP_CREATED, SignerStatus.BLOCKED);
        assertEquals(Instant.now().toEpochMilli(), signer.getTimestampLastUpdated().toEpochMilli(), MILLISECONDS_DELTA);
    }

    @Test
    void testUpdateStatusWhenCertificatesAreRevokedAndLastOneFailsThenOnlyRevokedOnesAreUpdatedInDatabase() throws Exception {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);
        createIssuedCertificateMetadata(signer.getId(), ISSUED_CERTIFICATE_1_SERIAL_NUMBER, Instant.now().plusSeconds(120));
        createIssuedCertificateMetadata(signer.getId(), ISSUED_CERTIFICATE_2_SERIAL_NUMBER, Instant.now().plusSeconds(120));

        final var request = new UpdateSignerStatusRequest(SignerStatus.REVOKED, null);

        doNothing()
            .doThrow(new RestClientException("Test REST client exception"))
            .when(ejbcaService)
                .revokeCertificate(any(EjbcaService.RevokeCertificateRequest.class));

        // when
        mockMvc.perform(put(SIGNER_ENDPOINT_WITH_ID, EXTERNAL_SIGNER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        // then
        final var signerAfterTest = signerRepository.findAll().iterator().next();
        assertSigner(signerAfterTest, TIMESTAMP_CREATED, SignerStatus.ACTIVE);

        final var issuedCertificatesMetadata = new ArrayList<IssuedCertificateMetadata>();
        issuedCertificateMetadataRepository.findAll()
                .forEach(issuedCertificatesMetadata::add);

        final var revokedCount = issuedCertificatesMetadata.stream()
                .filter(i -> i.getStatus() == IssuedCertificateStatus.REVOKED)
                .count();
        assertEquals(1, revokedCount);

        final var failedCount = issuedCertificatesMetadata.stream()
                .filter(i -> i.getStatus() == IssuedCertificateStatus.ISSUED)
                .count();
        assertEquals(1, failedCount);
    }

    @Test
    void testUpdateStatusWhenCertificatesAreRevokedThenSignerAndIssuedCertificateMetadataAreUpdatedInDatabase() throws Exception {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);
        final var certificateExpirationTimestamp = Instant.now().plusSeconds(120);
        createIssuedCertificateMetadata(signer.getId(), ISSUED_CERTIFICATE_1_SERIAL_NUMBER, certificateExpirationTimestamp);

        final var request = new UpdateSignerStatusRequest(SignerStatus.REVOKED, null);

        // when
        mockMvc.perform(put(SIGNER_ENDPOINT_WITH_ID, EXTERNAL_SIGNER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // then
        final var signerAfterTest = signerRepository.findAll().iterator().next();
        assertSigner(signerAfterTest, TIMESTAMP_CREATED, SignerStatus.REVOKED);
        assertEquals(Instant.now().toEpochMilli(), signerAfterTest.getTimestampLastUpdated().toEpochMilli(), MILLISECONDS_DELTA);

        final var issuedCertificatesMetadata = issuedCertificateMetadataRepository.findAll().iterator().next();
        assertRevokedIssuedCertificateMetadata(issuedCertificatesMetadata, signer.getId(), certificateExpirationTimestamp);
    }

    @Test
    void testUpdateStatusWhenCertificateIsAlreadyRevokedThenSignerAndIssuedCertificateAreUpdatedInDatabase() throws Exception {
        // given
        final var signer = createSigner(SignerStatus.ACTIVE);
        final var certificateExpirationTimestamp = Instant.now().plusSeconds(120);
        createIssuedCertificateMetadata(signer.getId(), ISSUED_CERTIFICATE_1_SERIAL_NUMBER, certificateExpirationTimestamp);

        final var request = new UpdateSignerStatusRequest(SignerStatus.REVOKED, RevocationReason.CA_COMPROMISE);

        final var exception = new RestClientException(
                "409: Conflict",
                HttpStatusCode.valueOf(409),
                """
                    {
                      "error_code" : 409,
                      "error_message" : "Certificate with issuer: %s and serial number: %s has previously been revoked. Revocation reason could not be changed or was not allowed."
                    }
                    """.formatted(CERTIFICATE_ISSUER_DN, ISSUED_CERTIFICATE_1_SERIAL_NUMBER),
                null,
                null);

        final var revokeCertificateRequest = EjbcaService.RevokeCertificateRequest.builder()
                .serialNumberHex(ISSUED_CERTIFICATE_1_SERIAL_NUMBER_HEX)
                .issuerDN(CERTIFICATE_ISSUER_DN)
                .revocationReason(RevocationReason.CA_COMPROMISE)
                .build();

        doThrow(exception)
                .when(ejbcaService)
                .revokeCertificate(revokeCertificateRequest);

        // when
        mockMvc.perform(put(SIGNER_ENDPOINT_WITH_ID, EXTERNAL_SIGNER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // then
        final var signerAfterTest = signerRepository.findAll().iterator().next();
        assertSigner(signerAfterTest, TIMESTAMP_CREATED, SignerStatus.REVOKED);
        assertEquals(Instant.now().toEpochMilli(), signerAfterTest.getTimestampLastUpdated().toEpochMilli(), MILLISECONDS_DELTA);

        final var issuedCertificatesMetadata = issuedCertificateMetadataRepository.findAll().iterator().next();
        assertRevokedIssuedCertificateMetadata(issuedCertificatesMetadata, signer.getId(), certificateExpirationTimestamp);
    }

    @Test
    void testGetDetailWhenSignerIsNotFoundThen400BadRequestResponseIsReturned() throws Exception {
        // given
        // -

        // when
        final var call = mockMvc.perform(get(SIGNER_ENDPOINT_WITH_ID, EXTERNAL_SIGNER_ID)
                        .contentType(MediaType.APPLICATION_JSON));

        // then
        call.andExpect(status().isBadRequest());
    }

    @Test
    void testGetDetailWhenSignerIsFoundThenResponseWithCorrectValuesIsReturned() throws Exception {
        // given
        createSigner(SignerStatus.ACTIVE);

        // when
        final var mvcResult = mockMvc.perform(get(SIGNER_ENDPOINT_WITH_ID, EXTERNAL_SIGNER_ID)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), SignerDetailResponse.class);
        assertSignerDetailResponse(responseBody);
    }

    private void assertSigner(final Signer signer, final Instant expectedTimestampCreated, final SignerStatus expectedStatus) {
        assertNotEquals(0, signer.getId());
        assertEquals(expectedTimestampCreated.toEpochMilli(), signer.getTimestampCreated().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(EXTERNAL_SIGNER_ID, signer.getExternalSignerId());
        assertEquals(USER_ID, signer.getUserId());
        assertEquals(CSR_BASE64, signer.getCsr());
        assertEquals(CERTIFICATE_DER_BASE64, signer.getCertificate());
        assertEquals(CERTIFICATE_EXPIRATION_TIMESTAMP.toEpochMilli(), signer.getTimestampCertificateExpiration().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(CERTIFICATE_CHAIN_BASE64, signer.getCertificateChain());
        assertEquals(expectedStatus, signer.getStatus());
    }

    private Signer createSigner(final SignerStatus status) {
        final var signer = Signer.builder()
                .timestampCreated(TIMESTAMP_CREATED)
                .externalSignerId(EXTERNAL_SIGNER_ID)
                .userId(USER_ID)
                .csr(CSR_BASE64)
                .certificate(CERTIFICATE_DER_BASE64)
                .timestampCertificateExpiration(CERTIFICATE_EXPIRATION_TIMESTAMP)
                .status(status)
                .certificateChainFromList(CERTIFICATE_CHAIN_BASE64)
                .build();
        return signerRepository.save(signer);
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

    private void createIssuedCertificateMetadata(final long singerId, final String serialNumber, final Instant expirationTimestamp) {
        final var issuedCertificateMetadata = IssuedCertificateMetadata.builder()
                .signer(AggregateReference.to(singerId))
                .timestampCreated(TIMESTAMP_CREATED)
                .serialNumber(serialNumber)
                .issuerDn(CERTIFICATE_ISSUER_DN)
                .timestampCertificateExpiration(expirationTimestamp)
                .status(IssuedCertificateStatus.ISSUED)
                .build();

        issuedCertificateMetadataRepository.save(issuedCertificateMetadata);
    }

    private void assertRevokedIssuedCertificateMetadata(final IssuedCertificateMetadata issuedCertificateMetadata, final long signerId, final Instant expirationTimestamp) {
        assertNotEquals(0, issuedCertificateMetadata.getId());
        assertEquals(signerId, issuedCertificateMetadata.getSigner().getId());
        assertEquals(TIMESTAMP_CREATED.toEpochMilli(), issuedCertificateMetadata.getTimestampCreated().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(Instant.now().toEpochMilli(), issuedCertificateMetadata.getTimestampLastUpdated().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(ISSUED_CERTIFICATE_1_SERIAL_NUMBER, issuedCertificateMetadata.getSerialNumber());
        assertEquals(CERTIFICATE_ISSUER_DN, issuedCertificateMetadata.getIssuerDn());
        assertEquals(expirationTimestamp.toEpochMilli(), issuedCertificateMetadata.getTimestampCertificateExpiration().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(IssuedCertificateStatus.REVOKED, issuedCertificateMetadata.getStatus());
    }

    private void assertErrorResponse(final ErrorResponse errorResponse, final ErrorCode errorCode, final String message) {
        assertEquals("ERROR", errorResponse.status());

        final var errorDetail = errorResponse.responseObject();
        assertEquals(errorCode, errorDetail.code());
        assertEquals(message, errorDetail.message());
    }
}
