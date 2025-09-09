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
@RequestMapping("api/documents")
@AllArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;

    @Operation(
            summary = "Uploads a document for signing",
            description = "Stores document in the database with its hash (calculated using the configured algorithm) and associates it with the Signer.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Successful upload",
                            content = @Content(schema = @Schema(implementation = UploadDocumentResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid input data or Signer not found"
                    )
            }
    )
    @PostMapping
    UploadDocumentResponse upload(
            @RequestParam("signerId") final String externalSignerId,
            @RequestParam("externalId") final String externalDocumentId,
            @RequestParam("name") final String documentName,
            @RequestParam("file") final MultipartFile file
    ) throws Throwable {
        logger.info("action: uploadDocument, state: initiated, externalSignerId: {}, externalDocumentId: {}", externalSignerId, externalDocumentId);
        final var result = documentService.uploadDocument(externalSignerId, externalDocumentId, documentName, file);
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
            description = "Verifies whether the document can be signed, if so, verifies the {@code signature} and creates a signed document.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Document signed successfully",
                            content = @Content(schema = @Schema(implementation = SignDocumentResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Signer or document not found, invalid document state or invalid signature"
                    )
            }
    )
    @PostMapping("/{documentId}/signature")
    SignDocumentResponse sign(@PathVariable final String documentId, @Valid @RequestBody final SignDocumentRequest requestBody) throws Throwable {
        logger.info("action: signDocument, state: initiated, documentId: {}", documentId);
        final var result = documentService.signDocument(documentId, requestBody);

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
            description = "Downloads the full content of the signed document, or a partial segment if the {@code Range} header is provided. " +
                    "In the case of multiple ranges, the server does not process the {@code Range} header but instead splits the response into parts exactly as requested by the client.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Full document content",
                            content = @Content(schema = @Schema(implementation = Resource.class))
                    ),
                    @ApiResponse(
                            responseCode = "206",
                            description = "Partial document content according to the {@code Range} header.",
                            content = @Content(schema = @Schema(implementation = Resource.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Document not found, not signed yet. See the error message for details"
                    )
            }
    )
    @GetMapping(value = "/{documentId}/file", produces = MediaType.APPLICATION_PDF_VALUE)
    Resource download(@PathVariable final String documentId, @RequestHeader(value = "Range", required = false) final String rangeHeader) throws Throwable {
        logger.info("action: downloadDocument, state: initiated, documentId: {}, ranges: {}", documentId, rangeHeader);
        final var result = documentService.downloadDocument(documentId);

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
            description = "Rejects a document and doesn't matter in which state it is. It sets the document state to REJECTED.",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Document rejected successfully",
                            content = @Content(schema = @Schema(implementation = RejectDocumentResponse.class))
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Document not found or invalid status in request body"
                    )
            }
    )
    @PutMapping("/{documentId}")
    RejectDocumentResponse reject(@PathVariable final String documentId, @Valid @RequestBody final RejectDocumentRequest requestBody) throws Throwable {
        logger.info("action: rejectDocument, state: initiated, documentId: {}", documentId);
        final var result = documentService.rejectDocument(documentId, requestBody);

        if (result.isSuccess()) {
            logger.info("action: rejectDocument, state: succeeded");
            return result.getResponse();
        } else {
            final var error = result.getError();
            logger.info("action: rejectDocument, state: failed, errorMessage: {}", error.getMessage());
            throw error;
        }
    }
}
