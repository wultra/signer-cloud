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


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

import static net.logstash.logback.argument.StructuredArguments.kv;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE;
import static reactor.netty.http.HttpConnectionLiveness.log;

/**
 * TODO
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@RestController
@RequestMapping(
        path = "/signature",
        produces = APPLICATION_JSON_VALUE
)
@AllArgsConstructor
@Slf4j
@Tag(
        name = "Signature requests",
        description = "Creation and management of remote document-signing requests via QTSP"
)
public class SignatureRequestController {

    private final SignatureService signatureService;

    @Operation(
            summary = "Create a document-signing request",
            description = """
                    Uploads a PDF document, obtains information about the selected
                    QTSP credential, prepares the PDF for signing, calculates the
                    digest to be authorized, and returns the QTSP authorization URL.
                    """
    )

    @PostMapping(path = "request", consumes = MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<CreateSignatureResponse> createSignatureRequest(
            @Parameter(
                    description = "Document and credential metadata",
                    required = true
            )
            @Valid
            @RequestPart("metadata")
            final CreateSignatureRequest request,

            @Parameter(
                    description = "PDF document to be signed",
                    required = true,
                    content = @Content(
                            mediaType = "application/pdf",
                            schema = @Schema(
                                    type = "string",
                                    format = "binary"
                            )
                    )
            )
            @RequestPart("file")
            final MultipartFile file
    ) {
        logger.info(
                "Creating signature request",
                kv("action", "createSignatureRequest"),
                kv("state", "initiated"),
                kv("documentExternalId", request.externalId()),
                kv("credentialId", request.credentialId()),
                kv("fileName", file.getOriginalFilename()),
                kv("fileSize", file.getSize())
        );

        try {
            final var response = signatureService.createSignatureRequest(request, file);

            final URI location = ServletUriComponentsBuilder
                    .fromCurrentRequest()
                    .path("/{signatureRequestId}")
                    .buildAndExpand(response.signatureRequestId())
                    .toUri();

            logger.info(
                    "Signature request created",
                    kv("action", "createSignatureRequest"),
                    kv("state", "succeeded"),
                    kv("signatureRequestId", response.signatureRequestId()),
                    kv("documentExternalId", request.externalId())
            );

            return ResponseEntity
                    .created(location)
                    .body(response);
        } catch (final RuntimeException e) {
            logger.warn(
                    "Signature request failed",
                    kv("action", "createSignatureRequest"),
                    kv("state", "failed"),
                    e
            );
            throw e;
        }
    }

    @GetMapping("/callback")
    public ResponseEntity<SignedDocumentResponse> processCallback(
            @Valid @ParameterObject @ModelAttribute final QTSPCallbackRequest request
    ) {
        logger.info("QTSP authorization callback received", kv("action", "processQtspCallback"),
                kv("state", "initiated"));

        try {
            final String signingTransactionId =
                    signatureService.completeSignature(
                            request.code(),
                            request.state()
                    );

            final URI downloadUrl =
                    ServletUriComponentsBuilder
                            .fromCurrentContextPath()
                            .path(
                                    "/api/v1/signature-requests/{transactionId}/document"
                            )
                            .buildAndExpand(signingTransactionId)
                            .toUri();

            log.info(
                    "QTSP authorization callback processed",
                    kv("action", "processQtspCallback"),
                    kv("state", "succeeded"),
                    kv(
                            "signingTransactionId",
                            signingTransactionId
                    )
            );
            return ResponseEntity.ok(
                    new SignedDocumentResponse(downloadUrl)
            );

        } catch (final RuntimeException e) {
            logger.warn("QTSP authorization callback failed", kv("action", "processQtspCallback"), kv("state", "failed"), e);
            throw e;
        }
    }
}
