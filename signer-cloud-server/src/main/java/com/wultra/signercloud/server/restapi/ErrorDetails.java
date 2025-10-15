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
package com.wultra.signercloud.server.restapi;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Details for {@link ErrorResponse}.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@Schema(description = "Details of the error")
public record ErrorDetails(
        @Schema(
                description = "Error code indicating the type of error",
                example = "RESOURCE_NOT_FOUND"
        )
        ErrorCode code,

        @Schema(
                description = "Message providing more details about the error",
                example = "Document with 9d18fb83-ea0f-4ce4-afc1-e4382a8222a5 not found"
        )
        String message
) {}
