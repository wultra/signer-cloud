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
package com.wultra.signercloud.server.signer;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * REST API response for details of a {@link Signer}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Schema(description = "Response with details of a `Signer`")
record SignerDetailResponse(
        @Schema(
                description = "ID of the signer",
                example = "756419e1-1d85-4172-815d-d8653ecd3a89",
                format = "uuid"
        )
        String externalSignerId,

        @Schema(
                description = "ID of the user owning the signer",
                example = "example-user-id"
        )
        String customUserId,

        @Schema(
                description = "Status of the signer",
                example = "ACTIVE"
        )
        SignerStatus signerStatus
) {
}