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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
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
    private static final String CSR_BASE64 = "MIHxMIGYAgEAMDYxETAPBgNVBAMMCEpvaG4gRG9lMRQwEgYDVQQKDAtFeGFtcGxlQ29ycDELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATr1DIu9x22bSEkt1lJP4hf6AgxHz2TyuiATaBW19huKMRB1XQKzdDd/ZKKL7fGaNmZCUEM2kmuCIv4W7eWnrZJoAAwCgYIKoZIzj0EAwIDSAAwRQIgbfepkGuhZMjVQ4alNWkD8xbDP6aufd9dWPfPTvKpaRcCIQDZu9uyj+tYEyPja0/D8Xk8HvDtkkVxpfoxbA2IMINiQA==";

    private static final String CSR_SIGNED_DATA_BASE64 = "MIGYAgEAMDYxETAPBgNVBAMMCEpvaG4gRG9lMRQwEgYDVQQKDAtFeGFtcGxlQ29ycDELMAkGA1UEBhMCVVMwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATr1DIu9x22bSEkt1lJP4hf6AgxHz2TyuiATaBW19huKMRB1XQKzdDd/ZKKL7fGaNmZCUEM2kmuCIv4W7eWnrZJoAA=";
    private static final String CSR_SIGNATURE_BASE64 = "MEUCIG33qZBroWTI1UOGpTVpA/MWwz+mrn3fXVj3z07yqWkXAiEA2bvbso/rWBMj42tPw/F5PB7w7ZJFcaX6MWwNiDCDYkA=";

    private static final String CERTIFICATE_DER_BASE64 = "MIIB+DCCAX6gAwIBAgIUQxSMGsgB+szrQpOV2AdlwcaPajwwCgYIKoZIzj0EAwMwFDESMBAGA1UEAwwJSXNzdWluZ0NBMB4XDTI1MDkxMTA4NDIxOFoXDTI3MDgxMTA5MTQ0NlowNjERMA8GA1UEAwwISm9obiBEb2UxFDASBgNVBAoMC0V4YW1wbGVDb3JwMQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABOvUMi73HbZtISS3WUk/iF/oCDEfPZPK6IBNoFbX2G4oxEHVdArN0N39koovt8Zo2ZkJQQzaSa4Ii/hbt5aetkmjgYswgYgwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAoBgNVHSUEITAfBggrBgEFBQcDAgYIKwYBBQUHAwQGCSqGSIb3LwEBBTAdBgNVHQ4EFgQU2PPiHgo5PGWHUhQNiylNjvsHIOIwDgYDVR0PAQH/BAQDAgXgMAoGCCqGSM49BAMDA2gAMGUCMQDKry6RV3+/65yDZA8o2Zib1iSYP3npwhUW+yJkNprn+vYoLpicCmNnxcRt3IEzx68CMCLZMBKfpPDQdo4jiO9OCNZstX2yUtFcHWN7Akvg+CyvFwFClfCWxr73icr2MYrxDw==";
    private static final Instant CERTIFICATE_EXPIRATION_TIMESTAMP = Instant.ofEpochMilli(1817975686000L);
    private static final Instant TIMESTAMP_CREATED = Instant.now().minusSeconds(120);
    private static final List<String> CERTIFICATE_CHAIN_BASE64 = List.of(
            "MIIBxzCCAU2gAwIBAgIUE0be+N9+2stvvu7y3BKDiHPWBVkwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTQ0N1oXDTI3MDgxMTA5MTQ0NlowFDESMBAGA1UEAwwJSXNzdWluZ0NBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEvq7LXohpIAISl1vcnH+8zMGFAyfEnyTOqTZAP40b9PzYmMLBbHGoDxvuJwdmF/mrXxfaQ9+Ki1/QRpkoLc6Ugsywu9agdA3Zu+54GPyxmTo8MvU/txcuRt1+7UMPxTAUo2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDaAAwZQIwMlzNpdVjPFt5/sac/ZVu/56n+vNiNFOywD8Ho8SjdDNnXeBBf3zoQ2aTwPdHtgCXAjEAkNCSl2buX5U3dsxavP2gcgjrxszNQGiQJ1AcRPL1ATHnaFrHwVGNqiFX5r9QQ7ud",
            "MIIBwzCCAUqgAwIBAgIUBiKRFuSkQ2w0B+eLnFGNCVBLTfwwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTMxMFoXDTM1MDgwOTA5MTMwOVowETEPMA0GA1UEAwwGUm9vdENBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEGTbosZgh/n+FWrbU7u05huRtjxUT8PE+fuFFHBtbcKNXYSl5Jf51gMBDn2dJKbM5oRsDLpl/nwscEvRKtibnw8AsIxXZYmyzBVA9meE5FGXswp6kAb/Sc4zQYo/O8RT5o2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBQ0YPm2cX21iY8xSP6EVfPBM2RNwTAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDZwAwZAIxAKsQhZDkBpxdGzn/gxDtqbtl5VtJFl3IJzXb36hWRf26P5Vha2vLAcFipD7koHF6bwIvYJHWRuq+SAzVYue9oId39+8AGKFXvzY+xDiSb/q7+ll/CwwQwcnoRundq8TSVYE="
    );

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
        signerRepository.deleteAll();
    }

    @Test
    void testCreateUpdateWhenSignatureVerificationFailsThenFailResponseIsReturned() throws Exception {
        // given
        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        when(powerAuthService.isSignatureValid(powerAuthRequest))
                .thenThrow(new PowerAuthClientException("PowerAuth test exception"));

        // when
        final var mvcResult = mockMvc.perform(post(CREATE_UPDATE_SIGNER_ENDPOINT)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andReturn();

        // then
        final var responseBody = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), SignerResponse.class);
        assertEquals(SignerResponseResult.FAIL, responseBody.result());
        assertEquals("Signature could not be verified due to PowerAuth error: PowerAuth test exception", responseBody.reason());
    }

    @Test
    void testCreateUpdateWhenCertificateEnrollmentFailsThenFailResponseIsReturned() throws Exception {
        // given
        final var certificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(CSR_BASE64)
                .externalSignerId(EXTERNAL_SIGNER_ID)
                .userId(USER_ID)
                .build();

        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        when(powerAuthService.isSignatureValid(powerAuthRequest)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenThrow(new RestClientException("EJBCA test exception"));

        // when
        final var mvcResult = mockMvc.perform(post(CREATE_UPDATE_SIGNER_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), SignerResponse.class);
        assertEquals(SignerResponseResult.FAIL, responseBody.result());
        assertEquals("Certificate could not be enrolled due to EJBCA error: EJBCA test exception", responseBody.reason());
    }

    @Test
    void testCreateUpdateWhenOperationFailsThenNothingIsStoredIntoDatabase() throws Exception {
        // given
        final var certificateRequest = EjbcaService.CertificateRequest.builder()
                .csr(CSR_BASE64)
                .externalSignerId(EXTERNAL_SIGNER_ID)
                .userId(USER_ID)
                .build();

        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        when(powerAuthService.isSignatureValid(powerAuthRequest)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenThrow(new RestClientException("EJBCA test exception"));

        // when
        mockMvc.perform(post(CREATE_UPDATE_SIGNER_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

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

        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        // when
        final var mvcResult = mockMvc.perform(post(CREATE_UPDATE_SIGNER_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), SignerResponse.class);
        assertEquals(SignerResponseResult.OK, responseBody.result());
        assertNull(responseBody.reason());
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

        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

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

        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

        // when
        final var mvcResult = mockMvc.perform(post(CREATE_UPDATE_SIGNER_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), SignerResponse.class);
        assertEquals(SignerResponseResult.OK, responseBody.result());
        assertNull(responseBody.reason());
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

        final var request = new CreateUpdateSignerRequest(EXTERNAL_SIGNER_ID, USER_ID, CSR_BASE64);

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
    void testUpdateStatusWhenSignerIsNotFoundThenFailResponseIsReturned() throws Exception {
        // given
        final var request = new UpdateSignerStatusRequest(SignerStatus.BLOCKED);

        // when
        final var mvcResult = mockMvc.perform(put(SIGNER_ENDPOINT_WITH_ID, EXTERNAL_SIGNER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), SignerResponse.class);
        assertEquals(SignerResponseResult.FAIL, responseBody.result());
        assertNotNull(responseBody.reason());
    }

    @Test
    void testUpdateStatusWhenStatusTransitionIsNotValidThenFailResponseIsReturned() throws Exception {
        // given
        createSigner(SignerStatus.REVOKED);
        final var request = new UpdateSignerStatusRequest(SignerStatus.ACTIVE);

        // when
        final var mvcResult = mockMvc.perform(put(SIGNER_ENDPOINT_WITH_ID, EXTERNAL_SIGNER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), SignerResponse.class);
        assertEquals(SignerResponseResult.FAIL, responseBody.result());
        assertNotNull(responseBody.reason());
    }

    @Test
    void testUpdateStatusWhenStatusTransitionIsValidThenSuccessResponseIsReturned() throws Exception {
        // given
        createSigner(SignerStatus.ACTIVE);
        final var request = new UpdateSignerStatusRequest(SignerStatus.BLOCKED);

        // when
        final var mvcResult = mockMvc.perform(put(SIGNER_ENDPOINT_WITH_ID, EXTERNAL_SIGNER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        // then
        final var responseBody = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), SignerResponse.class);
        assertEquals(SignerResponseResult.OK, responseBody.result());
        assertNull(responseBody.reason());
    }

    @Test
    void testUpdateStatusWhenStatusTransitionIsValidThenStatusIsUpdatedInDatabase() throws Exception {
        // given
        createSigner(SignerStatus.ACTIVE);
        final var request = new UpdateSignerStatusRequest(SignerStatus.BLOCKED);

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

    private void createSigner(final SignerStatus status) {
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
        signerRepository.save(signer);
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
}
