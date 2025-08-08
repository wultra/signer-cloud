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

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * Specialization of {@link Converter} for converting Base64 encoded strings to byte arrays.
 * This is used for converting Base64 encoded properties in the configuration.
 *
 * @author Lubos Racansky, lubos.racansky@wultra.com
 */
@Component
@ConfigurationPropertiesBinding
class Base64ToByteArrayConverter implements Converter<String, byte[]> {
    @Override
    public byte[] convert(final String source) {
        return Base64.getDecoder().decode(source);
    }
}
