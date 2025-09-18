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
package com.wultra.signercloud.server.configuration;

import com.wultra.signercloud.server.utils.CertificateUtils;
import eu.europa.esig.dss.model.x509.CertificateToken;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.security.cert.CertificateException;

/**
 * Specialization of {@link Converter} for converting an X.509 certificate from a Base64-encoded DER string into a {@link CertificateToken}.
 * Used for handling Base64-encoded properties in the configuration.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Component
@ConfigurationPropertiesBinding
public class Base64ToCertificateTokenConverter implements Converter<String, CertificateToken> {

    @Override
    public CertificateToken convert(@NonNull final String source) {
        try {
            final var x509Certificate = CertificateUtils.base64ToX509Certificate(source);
            return new CertificateToken(x509Certificate);
        } catch (final CertificateException e) {
            throw new IllegalArgumentException("Cannot convert base64 string to CertificateToken", e);
        }
    }
}
