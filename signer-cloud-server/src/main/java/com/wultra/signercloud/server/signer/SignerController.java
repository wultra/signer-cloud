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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for {@link Signer} operations.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@RestController
@RequestMapping("api/signers")
@AllArgsConstructor
class SignerController {

    private final SignerService signerService;

    @Operation(
            summary = "Create a new signer or update an existing one",
            description = "Creates a new signer with the provided data. If a signer with the provided {@code externalSignerId} " +
                    "already exists, it is updated. In both cases, activation is checked in PowerAuth, and a certificate " +
                    "is generated in EJBCA from the provided {@code csr}.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "REST API call was successful. Check the 'result' field in the response to determine if the signer was actually created.",
                            content = @Content(schema = @Schema(implementation = SignerResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid input data"
                    )
            }
    )
    @PostMapping
    SignerResponse crateUpdate(@Valid @RequestBody final CreateUpdateSignerRequest requestBody) {
        return signerService.createUpdateSigner(requestBody);
    }

}
