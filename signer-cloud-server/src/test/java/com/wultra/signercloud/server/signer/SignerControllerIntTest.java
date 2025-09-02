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
import com.wultra.signercloud.server.ejbca.EjbcaService;
import com.wultra.signercloud.server.powerauth.PowerAuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    private static final String DUMMY_EXTERNAL_SIGNER_ID = "dummyExternalSignerId";
    private static final String DUMMY_USER_ID = "dummyUserId";
    private static final String DUMMY_CSR = "dummyCsr";

    private static final String DUMMY_CERTIFICATE = "dummyCertificate";
    private static final long DUMMY_CERTIFICATE_EXPIRATION_SECONDS = 3_600;
    private static final Instant DUMMY_TIMESTAMP_CREATED = Instant.now().minusSeconds(120);

    private static final String CREATE_UPDATE_SIGNER_ENDPOINT = "/api/signers";
    private static final String SIGNER_ENDPOINT_WITH_ID = "/api/signers/{externalSignerId}";

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

    @Mock
    private X509Certificate certificate;

    @AfterEach
    void tearDown() {
        signerRepository.deleteAll();
    }

    @Test
    void testCreateUpdateWhenOperationFailsThenFailResponseIsReturned() throws Exception {
        // given
        final var request = new CreateUpdateSignerRequest(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_USER_ID, DUMMY_CSR);

        // when
        final var mvcResult = mockMvc.perform(post(CREATE_UPDATE_SIGNER_ENDPOINT)
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
    void testCreateUpdateWhenOperationFailsThenNothingIsStoredIntoDatabase() throws Exception {
        // given
        final var request = new CreateUpdateSignerRequest(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_USER_ID, DUMMY_CSR);

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
                .csr(DUMMY_CSR)
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .userId(DUMMY_USER_ID)
                .build();

        when(powerAuthService.isRegistrationActive(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenReturn(certificate);
        when(certificate.getEncoded()).thenReturn(Base64.getDecoder().decode(DUMMY_CERTIFICATE));
        when(certificate.getNotAfter()).thenReturn(Date.from(Instant.now().plusSeconds(DUMMY_CERTIFICATE_EXPIRATION_SECONDS)));

        final var request = new CreateUpdateSignerRequest(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_USER_ID, DUMMY_CSR);

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
                .csr(DUMMY_CSR)
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .userId(DUMMY_USER_ID)
                .build();

        when(powerAuthService.isRegistrationActive(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenReturn(certificate);
        when(certificate.getEncoded()).thenReturn(Base64.getDecoder().decode(DUMMY_CERTIFICATE));
        when(certificate.getNotAfter()).thenReturn(Date.from(Instant.now().plusSeconds(DUMMY_CERTIFICATE_EXPIRATION_SECONDS)));

        final var request = new CreateUpdateSignerRequest(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_USER_ID, DUMMY_CSR);

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
                .csr(DUMMY_CSR)
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .userId(DUMMY_USER_ID)
                .build();

        when(powerAuthService.isRegistrationActive(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenReturn(certificate);
        when(certificate.getEncoded()).thenReturn(Base64.getDecoder().decode(DUMMY_CERTIFICATE));
        when(certificate.getNotAfter()).thenReturn(Date.from(Instant.now().plusSeconds(DUMMY_CERTIFICATE_EXPIRATION_SECONDS)));

        final var request = new CreateUpdateSignerRequest(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_USER_ID, DUMMY_CSR);

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
                .csr(DUMMY_CSR)
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .userId(DUMMY_USER_ID)
                .build();

        when(powerAuthService.isRegistrationActive(DUMMY_EXTERNAL_SIGNER_ID)).thenReturn(true);
        when(ejbcaService.enrollCertificate(certificateRequest)).thenReturn(certificate);
        when(certificate.getEncoded()).thenReturn(Base64.getDecoder().decode(DUMMY_CERTIFICATE));
        when(certificate.getNotAfter()).thenReturn(Date.from(Instant.now().plusSeconds(DUMMY_CERTIFICATE_EXPIRATION_SECONDS)));

        final var request = new CreateUpdateSignerRequest(DUMMY_EXTERNAL_SIGNER_ID, DUMMY_USER_ID, DUMMY_CSR);

        // when
        mockMvc.perform(post(CREATE_UPDATE_SIGNER_ENDPOINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // then
        final var signer = signerRepository.findAll().iterator().next();
        assertSigner(signer, DUMMY_TIMESTAMP_CREATED, SignerStatus.ACTIVE);
        assertEquals(Instant.now().toEpochMilli(), signer.getTimestampLastUpdated().toEpochMilli(), MILLISECONDS_DELTA);
    }

    @Test
    void testUpdateStatusWhenSignerIsNotFoundThenFailResponseIsReturned() throws Exception {
        // given
        final var request = new UpdateSignerStatusRequest(SignerStatus.BLOCKED);

        // when
        final var mvcResult = mockMvc.perform(put(SIGNER_ENDPOINT_WITH_ID, DUMMY_EXTERNAL_SIGNER_ID)
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
        final var mvcResult = mockMvc.perform(put(SIGNER_ENDPOINT_WITH_ID, DUMMY_EXTERNAL_SIGNER_ID)
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
        final var mvcResult = mockMvc.perform(put(SIGNER_ENDPOINT_WITH_ID, DUMMY_EXTERNAL_SIGNER_ID)
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
        mockMvc.perform(put(SIGNER_ENDPOINT_WITH_ID, DUMMY_EXTERNAL_SIGNER_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // then
        final var signer = signerRepository.findAll().iterator().next();
        assertSigner(signer, DUMMY_TIMESTAMP_CREATED, SignerStatus.BLOCKED);
        assertEquals(Instant.now().toEpochMilli(), signer.getTimestampLastUpdated().toEpochMilli(), MILLISECONDS_DELTA);
    }

    @Test
    void testGetDetailWhenSignerIsNotFoundThen400BadRequestResponseIsReturned() throws Exception {
        // given
        // -

        // when
        final var call = mockMvc.perform(get(SIGNER_ENDPOINT_WITH_ID, DUMMY_EXTERNAL_SIGNER_ID)
                        .contentType(MediaType.APPLICATION_JSON));

        // then
        call.andExpect(status().isBadRequest());
    }

    @Test
    void testGetDetailWhenSignerIsFoundThenResponseWithCorrectValuesIsReturned() throws Exception {
        // given
        createSigner(SignerStatus.ACTIVE);

        // when
        final var mvcResult = mockMvc.perform(get(SIGNER_ENDPOINT_WITH_ID, DUMMY_EXTERNAL_SIGNER_ID)
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
        assertEquals(DUMMY_EXTERNAL_SIGNER_ID, signer.getExternalSignerId());
        assertEquals(DUMMY_USER_ID, signer.getUserId());
        assertEquals(DUMMY_CSR, signer.getCsr());
        assertEquals(DUMMY_CERTIFICATE, signer.getCertificate());
        assertEquals(Instant.now().plusSeconds(DUMMY_CERTIFICATE_EXPIRATION_SECONDS).toEpochMilli(), signer.getTimestampCertificateExpiration().toEpochMilli(), MILLISECONDS_DELTA);
        assertEquals(expectedStatus, signer.getStatus());
    }

    private void createSigner(final SignerStatus status) {
        final var signer = Signer.builder()
                .timestampCreated(DUMMY_TIMESTAMP_CREATED)
                .externalSignerId(DUMMY_EXTERNAL_SIGNER_ID)
                .userId(DUMMY_USER_ID)
                .csr(DUMMY_CSR)
                .certificate(DUMMY_CERTIFICATE)
                .timestampCertificateExpiration(Instant.now().plusSeconds(DUMMY_CERTIFICATE_EXPIRATION_SECONDS))
                .status(status)
                .build();
        signerRepository.save(signer);
    }

    private void assertSignerDetailResponse(final SignerDetailResponse response) {
        assertEquals(DUMMY_EXTERNAL_SIGNER_ID, response.externalSignerId());
        assertEquals(DUMMY_USER_ID, response.userId());
        assertEquals(SignerStatus.ACTIVE, response.signerStatus());
    }
}
