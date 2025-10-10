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
import jakarta.validation.constraints.NotEmpty;

/**
 * REST API request for signing the {@link Document}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Schema(description = "Request to sign a document")
record SignDocumentRequest(
        @Schema(
                description = "Signature of the document",
                example = "MEUCIA2qnAC9/Iv/WXeacSPzV2G+k+6CyDx/TU7sl8KcfynBAiEApa+s/gSca5MPsdUc+ZjCfbS/ZW3bqGu2tZ3oMPxCUrc=",
                format = "byte")
        @NotEmpty String signature,

        @Schema(
                description = "PAdES baseline signature level. If not specified, the level from the server configuration is used.",
                example = "PADES_B_B",
                format = "enum",
                defaultValue = "null"
        )
        DocumentSignatureLevel signatureLevel
) {}