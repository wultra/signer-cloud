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


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * TODO
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
public record QTSPCallbackRequest(

        @Schema(
                description = """
                        Authorization code issued by the QTSP authorization server.
                        The code is single-use and exchanged for a credential-scoped
                        access token.
                        """,
                example = "SplxlOBeZQQYbYS6WxSbIA"
        )
        @NotBlank
        @Size(max = 2048)
        String code,

        @Schema(
                description = """
                        OAuth state value generated when the signing transaction
                        was created. It identifies and protects the transaction.
                        """,
                example = "8vP3bf7ehfKXH2D8j6WpP9iP1q0nBzYu"
        )
        @NotBlank
        @Size(max = 512)
        String state
) {
}
