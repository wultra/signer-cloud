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

/**
 * REST API response for {@link Document} rejection operation.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
record RejectDocumentResponse(
        @Schema(description = "Unique identifier of the document in UUID format", example = "9a19a657-24a5-4d9f-9b72-f6b66de8f2c4")
        String documentId,

        @Schema(description = "Display name of the document.", example = "Customer Contract")
        String name,

        @Schema(description = "Original filename of the uploaded document.", example = "contract.pdf")
        String filename,

        @Schema(description = "Size of the document in bytes.", example = "27531")
        int size,

        @Schema(description = "Hash of the document content.", example = "x/PQFGarKCBiFs2lzQkH4QDtxBqR+6e6YQSomQEWv+U=")
        String hash
) {}