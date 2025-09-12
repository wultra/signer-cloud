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
 * REST API response for {@link Document} signing operation.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Schema(description = "Response for document signing operation")
@Builder
record SignDocumentResponse(
        @Schema(description = "Identifier of the signed document as UUID", example = "3f5e8c7a-2d91-4f9b-bc3e-1a7d2f8e6c42")
        String documentId,

        @Schema(description = "URI to access the signed document. It uses X-Forwarded* headers from request for building the URI.",
                example = "https://signercloud.com:8080/documents/3f5e8c7a-2d91-4f9b-bc3e-1a7d2f8e6c42/download")
        String uri
) {}