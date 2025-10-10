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
import com.wultra.signercloud.server.signer.Signer;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.annotation.Id;
import org.springframework.data.jdbc.core.mapping.AggregateReference;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Sequence;
import org.springframework.data.relational.core.mapping.Table;

import java.io.IOException;
import java.time.Instant;

/**
 * Data Access Object for the {@code sc_document} table.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Getter
@Builder(toBuilder = true)
@Table("sc_document")
public class Document {

    @Id
    @Sequence("sc_document_seq")
    private long id;

    private Instant timestampCreated;

    private Instant timestampLastUpdated;

    private String documentId;

    @Column("signer_id")
    private AggregateReference<Signer, Long> signer;

    private String externalId;

    private String documentName;

    private String fileName;

    private int fileSize;

    @Column("document_content_id")
    private AggregateReference<DocumentContent, Long> documentContent;

    private String hash;

    private DocumentStatus status;

    private String signature;

    private DocumentSignatureLevel signatureLevel;

    private String visualSignatureJson;

    /**
     * Returns {@link #getVisualSignatureJson()} as {@link DocumentVisualSignature}.
     *
     * @return document visual signature
     * @throws DocumentVisualSignatureException if serialization from String to object fails
     */
    public DocumentVisualSignature getVisualSignature() {
        try {
            if (visualSignatureJson == null) {
                return null;
            }

            return new ObjectMapper().readValue(visualSignatureJson, DocumentVisualSignature.class);
        } catch (final IOException e) {
            throw new DocumentVisualSignatureException("Problem with deserialization", e);
        }
    }

    public static class DocumentBuilder {

        /**
         * Set {@link #visualSignatureJson} from {@link DocumentVisualSignature} object.
         *
         * @param visualSignature the visual signature of the document to be serialized as JSON
         * @return builder instance
         */
        public DocumentBuilder visualSignature(final DocumentVisualSignature visualSignature) {
            try {
                if (visualSignature == null) {
                    return this;
                }

                this.visualSignatureJson = new ObjectMapper().writeValueAsString(visualSignature);
                return this;
            } catch (final JsonProcessingException e) {
                throw new DocumentVisualSignatureException("Problem with serialization", e);
            }
        }

    }
}
