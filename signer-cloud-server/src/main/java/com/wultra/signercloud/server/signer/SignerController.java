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

import com.wultra.core.rest.client.base.RestClientException;
import com.wultra.signercloud.server.restapi.Try;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Controller for {@link Signer} operations.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@RestController
@RequestMapping("signers")
@AllArgsConstructor
@Slf4j
class SignerController {

    private final SignerService signerService;

    @Operation(
            summary = "Create a new signer or update an existing one",
            description = "Creates a new signer with the provided data. If a signer with the specified `externalSignerId` " +
                    "already exists, it is updated. In both cases, the signature in `csr` is verified using PowerAuth, " +
                    "and a certificate is generated in EJBCA.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "REST API call was successful."
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "In case of error. Check response body for details."
                    )
            }
    )
    @PostMapping
    void createUpdate(@Valid @RequestBody final CreateUpdateSignerRequest requestBody) {
        logger.info("action: createUpdateSigner, state: initiated, userId: {}, externalSignerId: {}", requestBody.userId(), requestBody.signerId());
        final var result = Try.execute(
                () -> signerService.createUpdateSigner(requestBody)
        );

        if (result.isSuccess()) {
            logger.info("action: createUpdateSigner, state: succeeded");
        } else {
            final var error = result.getError();
            logger.error("action: createUpdateSigner, state: failed, errorMessage: {}", error.getMessage());
            throw error;
        }
    }

    @Operation(
            summary = "Change status of a signer",
            description = "Change status of Signer identified by `externalSignerId`. If status is changed to `REVOKED`, " +
                    "then EJBCA is called and all certificates linked to the Signer are invalidated",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "REST API call was successful."
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "In case of error. Check response body for details."
                    )
            }
    )
    @PutMapping("/{externalSignerId}")
    void updateStatus(@PathVariable final String externalSignerId, @Valid @RequestBody final UpdateSignerStatusRequest requestBody) {
        logger.info("action: updateSignerStatus, state: initiated, externalSignerId: {}, newStatus: {}", externalSignerId, requestBody.signerStatus());
        final var result =  Try.execute(
                () -> signerService.updateStatus(externalSignerId, requestBody)
        );

        if (result.isSuccess()) {
            logger.info("action: updateSignerStatus, state: succeeded");
        } else {
            final var error = result.getError();
            logger.error("action: updateSignerStatus, state: failed, errorMessage: {}", error.getMessage());
            throw error;
        }
    }

    @Operation(
            summary = "Gets details of a signer",
            description = "Gets the details of a Signer, including `userId` and `signerStatus`.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Response contains details of the signer",
                            content = @Content(schema = @Schema(implementation = SignerDetailResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Signer for given `externalSignerId` not found"
                    )
            }
    )
    @GetMapping("/{externalSignerId}")
    SignerDetailResponse getDetail(@PathVariable final String externalSignerId) {
        logger.info("action: getSignerDetail, state: initiated, externalSignerId: {}", externalSignerId);
        final var result = Try.execute(
                () -> signerService.getDetail(externalSignerId)
        );

        if (result.isSuccess()) {
            logger.info("action: getSignerDetail, state: succeeded");
            return result.getResponse();
        } else {
            final var error = result.getError();
            logger.info("action: getSignerDetail, state: failed, errorMessage: {}", error.getMessage());
            throw error;
        }
    }

}
