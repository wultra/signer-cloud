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

import com.wultra.signercloud.server.restapi.ErrorResponse;
import com.wultra.signercloud.server.restapi.Try;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * Controller for {@link Document} operations.
 *
 * @author Michal Rozehnal, michal.rozehnal@wultra.com
 */
@RestController
@RequestMapping("documents")
@AllArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;

    @Operation(
            summary = "Uploads a document for signing",
            description = """
                    Stores document in the database with its hash (calculated using the configured algorithm) and associates it with the `Signer`.
                    Because the `hash` of the document (including signature metadata) is calculated at this step, the document cannot be updated later.
                    For example, this affects the signature timestamp in the signed document, since the time of upload is used rather than the time when the document is actually signed (assembled).
                    """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Document uploaded successfully",
                            content = @Content(schema = @Schema(implementation = UploadDocumentResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid input data or Signer not found",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    @PostMapping
    UploadDocumentResponse upload(
            @Schema(
                    description = "ID of the signer",
                    example = "756419e1-1d85-4172-815d-d8653ecd3a89"
            )
            @RequestParam("externalSignerId") final String externalSignerId,

            @Schema(
                    description = "External ID of the document",
                    example = "example-document-id"
            )
            @RequestParam("externalId") final String externalId,

            @Schema(
                    description = "Name of the document",
                    example = "Document Contract"
            )
            @RequestParam("name") final String documentName,

            @Schema(
                    description = "File to be uploaded",
                    type = "string",
                    format = "binary"
            )
            @RequestParam("file") final MultipartFile file,

            @Schema(
                    description = "Definition of visual signature in document",
                    type = "string",
                    format = "json"
            )
            @RequestPart(value = "visualSignature", required = false) final DocumentVisualSignature visualSignature
    ) {
        logger.info("action: uploadDocument, state: initiated, externalSignerId: {}, documentExternalId: {}", externalSignerId, externalId);
        final var result = Try.execute(
                () -> documentService.uploadDocument(externalSignerId, externalId, documentName, file, visualSignature)
        );

        if (result.isSuccess()) {
            final var response = result.getResponse();
            logger.info("action: uploadDocument, state: succeeded, documentId: {}, hash: {}", response.documentId(), response.hash());
            return response;
        } else {
            logger.error("action: uploadDocument, state: failed, errorMessage: {}", result.getError().getMessage());
            throw result.getError();
        }
    }

    @Operation(
            summary = "Signs an uploaded document",
            description = "Verifies whether the document can be signed, if so, verifies the `signature` and creates a signed document.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Document signed successfully",
                            content = @Content(schema = @Schema(implementation = SignDocumentResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Signer or document not found, illegal document state or invalid signature",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Certificate processing error",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "503",
                            description = "PowerAuth service is not available or TSA url not set (if `PADES_B_T` signature level is requested)",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    @PostMapping("/{documentId}/signature")
    SignDocumentResponse sign(
            @Schema(
                    description = "ID of the document",
                    example = "9d18fb83-ea0f-4ce4-afc1-e4382a8222a5",
                    format = "uuid"
            )
            @PathVariable final String documentId,
            @Valid @RequestBody final SignDocumentRequest requestBody) {
        logger.info("action: signDocument, state: initiated, documentId: {}", documentId);
        final var result = Try.execute(
                () -> documentService.signDocument(documentId, requestBody)
        );

        if (result.isSuccess()) {
            logger.info("action: signDocument, state: succeeded");
            return result.getResponse();
        } else {
            logger.info("action: signDocument, state: failed, errorMessage: {}", result.getError().getMessage());
            throw result.getError();
        }
    }

    @Operation(
            summary = "Downloads signed document",
            description = "Downloads the full content of the signed document, or a partial segment if the `Range` header is provided. " +
                    "In the case of multiple ranges, the server does not process the `Range` header but instead splits the response into parts exactly as requested by the client.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Full document content",
                            content = @Content(schema = @Schema(implementation = Resource.class))
                    ),
                    @ApiResponse(
                            responseCode = "206",
                            description = "Partial document content according to the `Range` header",
                            content = @Content(schema = @Schema(implementation = Resource.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Document not found or not signed yet",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    @GetMapping(value = "/{documentId}/file", produces = MediaType.APPLICATION_PDF_VALUE)
    Resource download(
            @Schema(
                    description = "ID of the document",
                    example = "9d18fb83-ea0f-4ce4-afc1-e4382a8222a5",
                    format = "uuid"
            )
            @PathVariable final String documentId,

            @Schema(
                    description = "Optional Range header to download a specific byte range of the document (according to RFC 7233). " +
                            "If not provided, the full document is returned.",
                    example = "bytes=0-1023"
            )
            @RequestHeader(value = "Range", required = false) final String rangeHeader
    ) {
        logger.info("action: downloadDocument, state: initiated, documentId: {}, ranges: {}", documentId, rangeHeader);
        final var result = Try.execute(
                () -> documentService.downloadDocument(documentId)
        );

        if (result.isSuccess()) {
            logger.info("action: downloadDocument, state: succeeded");
            return result.getResponse();
        } else {
            final var error = result.getError();
            logger.info("action: downloadDocument, state: failed, errorMessage: {}", error.getMessage());
            throw error;
        }
    }

    @Operation(
            summary = "Rejects a document",
            description = "Rejects a document, regardless of its state. It sets the document state to `REJECTED`.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Document rejected successfully",
                            content = @Content(schema = @Schema(implementation = RejectDocumentResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Document not found or invalid request",
                            content = @Content(schema = @Schema(implementation = ErrorResponse.class))
                    )
            }
    )
    @PutMapping("/{documentId}")
    RejectDocumentResponse reject(
            @Schema(
                    description = "ID of the document",
                    example = "9d18fb83-ea0f-4ce4-afc1-e4382a8222a5",
                    format = "uuid"
            )
            @PathVariable final String documentId,
            @Valid @RequestBody final RejectDocumentRequest requestBody
    ) {
        logger.info("action: rejectDocument, state: initiated, documentId: {}", documentId);
        final var result = Try.execute(
                () -> documentService.rejectDocument(documentId, requestBody)
        );

        if (result.isSuccess()) {
            logger.info("action: rejectDocument, state: succeeded");
            return result.getResponse();
        } else {
            final var error = result.getError();
            logger.info("action: rejectDocument, state: failed, errorMessage: {}", error.getMessage());
            throw error;
        }
    }

    @Operation(
            summary = "Deletes a document",
            description = "Permanently deletes a document, regardless of its state. This operation cannot be undone.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Document deleted successfully"
                    )
            }
    )
    @DeleteMapping("/{documentId}")
    void delete(
            @Schema(
                    description = "ID of the document",
                    example = "9d18fb83-ea0f-4ce4-afc1-e4382a8222a5",
                    format = "uuid"
            )
            @PathVariable final String documentId
    ) {
        logger.info("action: deleteDocument, state: initiated, documentId: {}", documentId);
        documentService.deleteDocument(documentId);
        logger.info("action: deleteDocument, state: succeeded");
    }
}
