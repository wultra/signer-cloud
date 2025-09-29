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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

/**
 * REST API response for {@link Document} upload operation.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Schema(description = "Response after uploading a document")
@Builder
record UploadDocumentResponse(
        @Schema(
                description = "ID of the uploaded document",
                example = "d290f1ee-6c54-4b01-90e6-d701748f0851",
                format = "uuid"
        )
        String documentId,

        @Schema(
                description = "ID of the signer associated with the document",
                example = "756419e1-1d85-4172-815d-d8653ecd3a89"
        )
        String signerId,

        @Schema(
                description = "External ID of the document",
                example = "example-document-id"
        )
        String externalId,

        @Schema(
                description = "Name of the document",
                example = "Contract Example"
        )
        String name,

        @Schema(
                description = "File name of the document",
                example = "Contract.pdf"
        )
        String fileName,

        @Schema(
                description = "Size of the document in bytes",
                example = "500"
        )
        int size,

        @Schema(
                description = "Hash of the document to be signed",
                example = "X48E9qOokqqrvdts8nOJRJN3OWDUoyWxBf7kbu9DBPE=",
                format = "byte"
        )
        String hash
) {
}
