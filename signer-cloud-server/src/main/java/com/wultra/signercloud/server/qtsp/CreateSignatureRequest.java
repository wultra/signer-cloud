/*
 * Signed Cloud
 * Copyright (C) 2026 Wultra s.r.o.
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
package com.wultra.signercloud.server.qtsp;


import com.wultra.signercloud.server.document.DocumentVisualSignature;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * TODO
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
public record CreateSignatureRequest(
        @Schema(
                description = "External document identifier assigned by the RP",
                example = "example-document-id"
        )
        @NotBlank
        @Size(max = 128)
        String externalId,

        @Schema(
                description = "Human-readable document name",
                example = "Employment contract"
        )
        @NotBlank
        @Size(max = 255)
        String name,

        @Schema(
                description = """
                        Identifier of the authenticated QTSP session whose service
                        access token will be used for credentials/info.
                        """,
                example = "qtsp-session-8d7f3f3e"
        )
        @NotBlank
        String qtspSessionId,

        @Schema(
                description = "QTSP credential selected by the signer",
                example = "credential-12345"
        )
        @NotBlank
        String credentialId,

        @Schema(
                description = "Optional visible-signature configuration"
        )
        @Valid
        DocumentVisualSignature visualSignature
) {
}
