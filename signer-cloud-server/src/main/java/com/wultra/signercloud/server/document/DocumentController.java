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
import org.springframework.http.ResponseEntity;
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

    @GetMapping("/{documentId}/file")
    ResponseEntity<byte[]> download(@PathVariable final String documentId, @RequestHeader(value = "Range", required = false) final String rangeHeader) throws Throwable {
        final var result = documentService.downloadDocument(documentId, rangeHeader);
        if (result.isSuccess()) {
            return result.getResponse();
        } else {
            throw result.getError();
        }
    }
}
