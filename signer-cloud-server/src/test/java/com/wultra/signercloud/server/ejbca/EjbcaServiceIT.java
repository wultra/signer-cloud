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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.security.cert.X509Certificate;

/**
 * Integration test for {@link EjbcaService}.
 * <p>
 * Fail-safe plugin is not configured on purpose to run this test only on demand.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@SpringBootTest
@ActiveProfiles("dev")
class EjbcaServiceIT {

    @Autowired
    private EjbcaService ejbcaService;

    @Test
    void testEnrollCertificate() throws Exception {
        final String csr = """
                -----BEGIN CERTIFICATE REQUEST-----
                MIICrjCCAZYCAQAwaTELMAkGA1UEBhMCQ1MxGzAZBgNVBAgMElRoZSBDemVjaCBS
                ZXB1YmxpYzEPMA0GA1UEBwwGUHJhZ3VlMRUwEwYDVQQKDAxXdWx0cmEsIEluYy4x
                FTATBgNVBAMMDCoud3VsdHJhLmNvbTCCASIwDQYJKoZIhvcNAQEBBQADggEPADCC
                AQoCggEBAKPiSmc+mzn9SCf7G84WBK6MmPxPlDT38cL8hWNkYGp7p8sxb+Kynx/G
                bzEhvscLsuxlawQ/5hyDAO2Pg9860fo02lLZxOAfme9W6IYH/ZIke4sEly670WIp
                cCXRbAwMc6GFwcIxyNywrvgwwcMmhJKcysu0sZ0RpB1j2fRfmes4LedlUqfb/R4w
                pyZRIp5dW9kOT3v1mHaGuMy9iRSB8QRRHsoSuNX8HxAKzP80IRlbs4Kn1SBcpQKI
                U6Y4R4YwkLas83JS+OMeLTDBeAWVAGsCGARc3Z9io39K66QNDwwgxAeqUWF5xB9H
                XSHyPNxl072lddjiQ4S6YWMVwlgZWjcCAwEAAaAAMA0GCSqGSIb3DQEBDQUAA4IB
                AQAwUJE9g0huEeW9QFAFQ9bBOPSwnCFTdbCua06xJIOH073iPwHQQ3ECIelaoOHS
                poyWTNl/aumpN0D1qjM9OHxBv8te9aAHweWOjG4fdm/S3Qyhx/zTap0tLBWBolPI
                7QbrA8pH5gpoMwTsT267hyYbQEaXyxn/ywK6Ev2etG4l7xh6aI4WDkwbgGzgTHwZ
                uba+K2iCssb3jK4kwoPF4WARmCwCs1g9+Q+HNN5GnEnhgHN9Uc2q4JZgx7/+jkja
                Pt0NeqcctnwtR+DbziukYzGdgJabFSXpQkRRf6vhgVm/350QXjFLpfBkEDKJtiS8
                cAuJQRzaWMiNnSXwsFSNvuQh
                -----END CERTIFICATE REQUEST-----
                """;

        final X509Certificate x509Certificate = ejbcaService.enrollCertificate(csr);
        System.out.println(x509Certificate);
        // TODO (racansky, 2025-08-08) add assertions and client certificate configuration
    }
}
