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

import com.wultra.signercloud.server.restapi.ErrorResponse;
import com.wultra.signercloud.server.restapi.Try;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import static net.logstash.logback.argument.StructuredArguments.kv;

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
                    "and a certificate is generated in Certificate Authority.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Signer created or updated successfully"
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid CSR or its signature",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Certificate processing error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "503",
                            description = "Problem with CSR verification via PowerAuth or certificate enrollment via Certificate Authority.",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    @PostMapping
    void createUpdate(@Valid @RequestBody final CreateUpdateSignerRequest requestBody) {
        logger.info("Create or update signer initiated", kv("action", "createUpdateSigner"), kv("state", "initiated"), kv("userId", requestBody.userId()), kv("externalSignerId", requestBody.externalSignerId()));
        final var result = Try.execute(
                () -> signerService.createUpdateSigner(requestBody)
        );

        if (result.isSuccess()) {
            logger.info("Create or update signer succeeded", kv("action", "createUpdateSigner"), kv("state", "succeeded"), kv("externalSignerId", requestBody.externalSignerId()));
        } else {
            final var error = result.getError();
            logger.error("Create or update signer failed", kv("action", "createUpdateSigner"), kv("state", "failed"), kv("externalSignerId", requestBody.externalSignerId()), kv("errorMessage", error.getMessage()));
            throw error;
        }
    }

    @Operation(
            summary = "Change status of a signer",
            description = "Change status of signer identified by `externalSignerId`. If status is changed to `REVOKED`, " +
                    "then Certificate Authority is called and all certificates linked to the signer are invalidated",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Status successfully changed"
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Signer not found, illegal signer state, or 4xx HTTP status returned by Certificate Authority",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "503",
                            description = "Certificate authority is not available",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    @PutMapping("/{externalSignerId}")
    void updateStatus(
            @Schema(
                    description = "ID of the signer",
                    example = "756419e1-1d85-4172-815d-d8653ecd3a89",
                    format = "uuid"
            )
            @PathVariable final String externalSignerId,
            @Valid @RequestBody final UpdateSignerStatusRequest requestBody) {
        logger.info("Update signer status initiated", kv("action", "updateSignerStatus"), kv("state", "initiated"), kv("externalSignerId", externalSignerId), kv("newStatus", requestBody.signerStatus()));
        final var result =  Try.execute(
                () -> signerService.updateStatus(externalSignerId, requestBody)
        );

        if (result.isSuccess()) {
            logger.info("Update signer status succeeded", kv("action", "updateSignerStatus"), kv("state", "succeeded"), kv("externalSignerId", externalSignerId));
        } else {
            final var error = result.getError();
            logger.error("Update signer status failed", kv("action", "updateSignerStatus"), kv("state", "failed"), kv("externalSignerId", externalSignerId), kv("errorMessage", error.getMessage()));
            throw error;
        }
    }

    @Operation(
            summary = "Gets details of a signer",
            description = "Gets the details of a signer, including `userId` and `signerStatus`.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Response with details of the signer",
                            content = @Content(schema = @Schema(implementation = SignerDetailResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Signer for given `externalSignerId` not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    @GetMapping("/{externalSignerId}")
    SignerDetailResponse getDetail(
            @Schema(
                    description = "ID of the signer",
                    example = "756419e1-1d85-4172-815d-d8653ecd3a89",
                    format = "uuid"
            )
            @PathVariable final String externalSignerId
    ) {
        logger.info("Get signer detail initiated", kv("action", "getSignerDetail"), kv("state", "initiated"), kv("externalSignerId", externalSignerId));
        final var result = Try.execute(
                () -> signerService.getDetail(externalSignerId)
        );

        if (result.isSuccess()) {
            logger.info("Get signer detail succeeded", kv("action", "getSignerDetail"), kv("state", "succeeded"), kv("externalSignerId", externalSignerId));
            return result.getResponse();
        } else {
            final var error = result.getError();
            logger.info("Get signer detail failed", kv("action", "getSignerDetail"), kv("state", "failed"), kv("externalSignerId", externalSignerId), kv("errorMessage", error.getMessage()));
            throw error;
        }
    }

}
