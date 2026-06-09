package com.wultra.signercloud.server.signer;

import com.wultra.signercloud.server.ejbca.EjbcaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link SignerService}.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Sql
@ExtendWith(MockitoExtension.class)
class SignerServiceIntegrationTest {

    private static final String CERTIFICATE_DER_BASE64 = "MIIB+DCCAX6gAwIBAgIUQxSMGsgB+szrQpOV2AdlwcaPajwwCgYIKoZIzj0EAwMwFDESMBAGA1UEAwwJSXNzdWluZ0NBMB4XDTI1MDkxMTA4NDIxOFoXDTI3MDgxMTA5MTQ0NlowNjERMA8GA1UEAwwISm9obiBEb2UxFDASBgNVBAoMC0V4YW1wbGVDb3JwMQswCQYDVQQGEwJVUzBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABOvUMi73HbZtISS3WUk/iF/oCDEfPZPK6IBNoFbX2G4oxEHVdArN0N39koovt8Zo2ZkJQQzaSa4Ii/hbt5aetkmjgYswgYgwDAYDVR0TAQH/BAIwADAfBgNVHSMEGDAWgBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAoBgNVHSUEITAfBggrBgEFBQcDAgYIKwYBBQUHAwQGCSqGSIb3LwEBBTAdBgNVHQ4EFgQU2PPiHgo5PGWHUhQNiylNjvsHIOIwDgYDVR0PAQH/BAQDAgXgMAoGCCqGSM49BAMDA2gAMGUCMQDKry6RV3+/65yDZA8o2Zib1iSYP3npwhUW+yJkNprn+vYoLpicCmNnxcRt3IEzx68CMCLZMBKfpPDQdo4jiO9OCNZstX2yUtFcHWN7Akvg+CyvFwFClfCWxr73icr2MYrxDw==";
    private static final String CSR = "MIIBXTCB5QIBADA2MQswCQYDVQQGEwJVUzEUMBIGA1UEChMLRXhhbXBsZUNvcnAxETAPBgNVBAMTCEpvaG4gRG9lMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEQ2Z9Zsg45e2YZ89B03uhjz7LSkXuuWJW+DvT03tfdD+5bmDutM7slZzgE9fz6saNuRoBTu07qe3QkJoG1iXDOYYuTDLBp813iJOwVplFsUs11m579zSmhU31GbAtM4f/oDAwLgYJKoZIhvcNAQkOMSEwHzAdBgNVHQ4EFgQU/aKAjBfH82uqVzN6uBUK3ydJ5IYwCgYIKoZIzj0EAwMDZwAwZAIwQ8qfBDToBmyFgu+6/QUdEBHP7y6MjkNiy4KiDgGl/CNSksWarK/v6U37t6jMq1X6AjAEdYVXpTQkOOLPhJc0HE3ZpG2w14YqV1zXtTu+nfjZ4kIwfHBRL7rS+/93XPA1Hok=";
    private static final List<String> CERTIFICATE_CHAIN_BASE64 = List.of(
            "MIIBxzCCAU2gAwIBAgIUE0be+N9+2stvvu7y3BKDiHPWBVkwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTQ0N1oXDTI3MDgxMTA5MTQ0NlowFDESMBAGA1UEAwwJSXNzdWluZ0NBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEvq7LXohpIAISl1vcnH+8zMGFAyfEnyTOqTZAP40b9PzYmMLBbHGoDxvuJwdmF/mrXxfaQ9+Ki1/QRpkoLc6Ugsywu9agdA3Zu+54GPyxmTo8MvU/txcuRt1+7UMPxTAUo2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBSdHZNQyT/Ly6g/w8deRDDhKZqpDjAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDaAAwZQIwMlzNpdVjPFt5/sac/ZVu/56n+vNiNFOywD8Ho8SjdDNnXeBBf3zoQ2aTwPdHtgCXAjEAkNCSl2buX5U3dsxavP2gcgjrxszNQGiQJ1AcRPL1ATHnaFrHwVGNqiFX5r9QQ7ud",
            "MIIBwzCCAUqgAwIBAgIUBiKRFuSkQ2w0B+eLnFGNCVBLTfwwCgYIKoZIzj0EAwMwETEPMA0GA1UEAwwGUm9vdENBMB4XDTI1MDgxMTA5MTMxMFoXDTM1MDgwOTA5MTMwOVowETEPMA0GA1UEAwwGUm9vdENBMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEGTbosZgh/n+FWrbU7u05huRtjxUT8PE+fuFFHBtbcKNXYSl5Jf51gMBDn2dJKbM5oRsDLpl/nwscEvRKtibnw8AsIxXZYmyzBVA9meE5FGXswp6kAb/Sc4zQYo/O8RT5o2MwYTAPBgNVHRMBAf8EBTADAQH/MB8GA1UdIwQYMBaAFDRg+bZxfbWJjzFI/oRV88EzZE3BMB0GA1UdDgQWBBQ0YPm2cX21iY8xSP6EVfPBM2RNwTAOBgNVHQ8BAf8EBAMCAYYwCgYIKoZIzj0EAwMDZwAwZAIxAKsQhZDkBpxdGzn/gxDtqbtl5VtJFl3IJzXb36hWRf26P5Vha2vLAcFipD7koHF6bwIvYJHWRuq+SAzVYue9oId39+8AGKFXvzY+xDiSb/q7+ll/CwwQwcnoRundq8TSVYE="
    );

    @Autowired
    private SignerService tested;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private EjbcaService ejbcaService;

    @Captor
    private ArgumentCaptor<EjbcaService.CertificateRequest> argumentCaptor;

    @Test
    void testCleanupSigners() throws Exception {
        final long result = tested.cleanupSigners(1);

        assertEquals(1, result);

        final Map<String, Object> callbackEvent = jdbcTemplate.queryForMap("SELECT * FROM sc_callback_event ORDER BY timestamp_created DESC LIMIT 1");
        assertNotNull(callbackEvent);
        assertEquals("EXPIRED", callbackEvent.get("CALLBACK_TYPE"));
        assertEquals("PROCESSING", callbackEvent.get("STATUS")); // since callback got 400, staying in the queue
        JSONAssert.assertEquals("""
                {"externalSignerId": "signer1", "userId": "user1", "callbackType": "EXPIRED", "certificateSerialNumber": "64309416018842723591211913217267439625813315032", "certificateExpiration": "2027-08-11T09:14:46Z"}""",
                callbackEvent.get("CALLBACK_DATA").toString(), false);
        assertNotNull(callbackEvent.get("IDEMPOTENCY_KEY"));
        assertNotNull(callbackEvent.get("TIMESTAMP_CREATED"));
    }

    @Test
    void testRenewSigners() throws Exception {
        final var certificateBytes = Base64.getDecoder().decode(CERTIFICATE_DER_BASE64);
        final var x509Certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new java.io.ByteArrayInputStream(certificateBytes));

        final var certificateResponse = EjbcaService.CertificateResponse.builder()
                .certificate(x509Certificate)
                .chain(CERTIFICATE_CHAIN_BASE64)
                .build();

        when(ejbcaService.enrollCertificate(any()))
                .thenReturn(certificateResponse);

        final long result = tested.renewSigners(3);

        assertEquals(1, result);

        verify(ejbcaService).enrollCertificate(argumentCaptor.capture());

        final var certificateRequest = argumentCaptor.getValue();
        assertEquals("signer2", certificateRequest.externalSignerId());
        assertEquals("user1", certificateRequest.userId());
        assertEquals(CSR, certificateRequest.csr());

        final Map<String, Object> callbackEvent = jdbcTemplate.queryForMap("SELECT * FROM sc_callback_event ORDER BY timestamp_created DESC LIMIT 1");
        assertNotNull(callbackEvent);
        assertEquals("RENEWED", callbackEvent.get("CALLBACK_TYPE"));
        assertEquals("PROCESSING", callbackEvent.get("STATUS")); // since callback got 400, staying in the queue
        JSONAssert.assertEquals("""
                {"externalSignerId": "signer2", "userId": "user1", "callbackType": "RENEWED", "certificateSerialNumber": "382960601382395725256979170171623638043940842044", "certificateExpiration": "2027-08-11T09:14:46Z"}""",
                callbackEvent.get("CALLBACK_DATA").toString(), false);
        assertNotNull(callbackEvent.get("IDEMPOTENCY_KEY"));
        assertNotNull(callbackEvent.get("TIMESTAMP_CREATED"));
    }
}
