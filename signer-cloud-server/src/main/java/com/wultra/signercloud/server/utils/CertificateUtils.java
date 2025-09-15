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
package com.wultra.signercloud.server.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;

/**
 * Utility class for certificate operations.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
public final class CertificateUtils {

    private CertificateUtils() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Converts a Base64-encoded X.509 certificate in DER format to an {@link X509Certificate} object.
     *
     * @param certificateBase64 the Base64-encoded X.509 certificate string in DER format
     * @return the X509Certificate object
     * @throws CertificateException if there is an error in the certificate format
     * @throws IOException if there is an I/O error during the conversion
     */
    public static X509Certificate base64ToX509Certificate(final String certificateBase64) throws CertificateException, IOException {
        final var certificateBytes = Base64.getDecoder().decode(certificateBase64);
        final var certificateFactory = CertificateFactory.getInstance("X.509");
        try (final var inputStream = new ByteArrayInputStream(certificateBytes)) {
            return (X509Certificate) certificateFactory.generateCertificate(inputStream);
        }
    }
}
