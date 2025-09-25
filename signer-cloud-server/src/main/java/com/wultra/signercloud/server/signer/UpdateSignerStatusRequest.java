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
import jakarta.validation.constraints.NotNull;

/**
 * REST API request body for changing the {@link SignerStatus} of a {@link Signer}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Schema(description = "Request to update the status of a signer")
record UpdateSignerStatusRequest(
        @Schema(description = "New status of the signer",
                example = "REVOKED")
        @NotNull SignerStatus signerStatus,

        @Schema(description = "If the new status is 'REVOKED', this is the reason for the revocation. By default it is set to 'UNSPECIFIED'.",
                example = "UNSPECIFIED")
        RevocationReason revocationReason
) {}