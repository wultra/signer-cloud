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
package com.wultra.signercloud.server.ejbca;

import com.wultra.core.rest.client.base.RestClientException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.security.cert.X509Certificate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration test for {@link EjbcaService}.
 * <p>
 * Fail-safe plugin is not configured on purpose to run this test only on demand.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@SpringBootTest
@ActiveProfiles("dev")
@Slf4j
class EjbcaServiceIT {

    @Autowired
    private EjbcaService ejbcaService;

    @Test
    void testEnrollCertificate() throws Exception {
        // openssl req -sha512 -new -subj "/C=CZ/O=Wultra SMOKE/CN=John Doe" -key wultra.com.key -out wultra.com.csr
        // openssl asn1parse -i -in wultra.com.csr
        final String csr = """
                -----BEGIN CERTIFICATE REQUEST-----
                MIICfDCCAWQCAQAwNzELMAkGA1UEBhMCQ1oxFTATBgNVBAoMDFd1bHRyYSBTTU9L
                RTERMA8GA1UEAwwISm9obiBEb2UwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEK
                AoIBAQCj4kpnPps5/Ugn+xvOFgSujJj8T5Q09/HC/IVjZGBqe6fLMW/isp8fxm8x
                Ib7HC7LsZWsEP+YcgwDtj4PfOtH6NNpS2cTgH5nvVuiGB/2SJHuLBJcuu9FiKXAl
                0WwMDHOhhcHCMcjcsK74MMHDJoSSnMrLtLGdEaQdY9n0X5nrOC3nZVKn2/0eMKcm
                USKeXVvZDk979Zh2hrjMvYkUgfEEUR7KErjV/B8QCsz/NCEZW7OCp9UgXKUCiFOm
                OEeGMJC2rPNyUvjjHi0wwXgFlQBrAhgEXN2fYqN/SuukDQ8MIMQHqlFhecQfR10h
                8jzcZdO9pXXY4kOEumFjFcJYGVo3AgMBAAGgADANBgkqhkiG9w0BAQ0FAAOCAQEA
                XUlodDJd1MSwvMsaO9lDhu1eT2E4lziXQnl5wp+pwHwyRtLHVle+A1jd+RU7vYJ6
                XMV+0uzreVwh7GqtYq/knfo6Xf+cnt3KtHy8HxXLjPZ4AYiuRNXcXIpMO4tLJeky
                KIldFzOoMc4EJFQVTyrYKmj5KEPuwlqCRw5E4LdOanEbayQss+xP4wt4hfMIYgdG
                dkxiqv4Q2U4ORLWrq0ZqyWdz1bfgjfwxudmb+HcXURwtUpQSoza63WWWw0YbxjDK
                Co5GHQEP3jrFuF/KQoUy1wuHv86xquuZp7aohlwpBUokJ/0BVXiB729/zKjECOWm
                kDjqUncVL0qPIOsk6yrjhw==
                -----END CERTIFICATE REQUEST-----
                """;

        final var request = EjbcaService.CertificateRequest.builder()
                .userId("user123")
                .externalSignerId(UUID.randomUUID().toString())
                .csr(csr)
                .build();

        try {
            final X509Certificate result = ejbcaService.enrollCertificate(request);
            logger.info("Got certificate: {}", result);
            assertEquals("C=CZ,O=Wultra SMOKE,CN=John Doe", result.getSubjectX500Principal().getName());
            assertEquals("SHA384withECDSA", result.getSigAlgName());
        } catch (final RestClientException e) {
            fail(e.getResponse());
        }
    }
}
