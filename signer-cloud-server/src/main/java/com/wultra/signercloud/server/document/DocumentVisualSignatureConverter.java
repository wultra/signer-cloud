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
package com.wultra.signercloud.server.document;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;

/**
 * Converter for {@link DocumentVisualSignature}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
public final class DocumentVisualSignatureConverter {

    private DocumentVisualSignatureConverter() {
        throw new IllegalStateException("Utility class");
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Converter from {@link DocumentVisualSignature} to JSON.
     */
    @WritingConverter
    public static class VisualSignatureToJsonConverter implements Converter<DocumentVisualSignature, String> {

        @Override
        public String convert(final @NonNull DocumentVisualSignature documentVisualSignature) {
            try {
                return OBJECT_MAPPER.writeValueAsString(documentVisualSignature);
            } catch (final JsonProcessingException e) {
                throw new DocumentVisualSignatureException("Serialization to JSON failed: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Converter from JSON to {@link DocumentVisualSignature}.
     */
    @ReadingConverter
    public static class JsonToVisualSignatureConverter implements Converter<String, DocumentVisualSignature> {

        @Override
        public DocumentVisualSignature convert(final @NonNull String source) {
            try {
                if (StringUtils.isBlank(source)) {
                    return null;
                }

                return OBJECT_MAPPER.readValue(source, DocumentVisualSignature.class);
            } catch (final JsonProcessingException e) {
                throw new DocumentVisualSignatureException("Deserialization from JSON failed" + e.getMessage(), e);
            }
        }
    }
}
